package org.remitot.m3u8.download;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Processor implements Runnable {
  protected final String[] args;
  protected final Proxy proxy;
  protected final Function<URL, URLConnection> connector;
  protected final String streamListUrl;
  protected final File outFolder;
  protected final File partsFolder;
  protected final PrintStream out = System.out;

  protected final AppLog appLog;

  public Processor(String[] args) {
    this.args = args;

    this.proxy = determineProxy(args);
    applyPkixWorkaround();

    this.connector = url -> {
      try {
        return proxy == null ? url.openConnection() : url.openConnection(proxy);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    this.streamListUrl = determineStreamListUrl(args);
    // verify URL for the user against cmd's escaping
    out.println("Verify param streamListUrl: >>>" + this.streamListUrl + "<<<");

    this.outFolder = determineOutFolder(args);
    prepareFolder(this.outFolder);
    out.println("Verify param outFolder: >>>" + this.outFolder.getAbsolutePath() + "<<<");

    this.partsFolder = new File(this.outFolder, "parts");
    prepareFolder(this.partsFolder);

    appLog = new AppLog(new File(this.outFolder, "app-log.txt"));
  }

  protected static void applyPkixWorkaround() {
    TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
      public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
      public void checkClientTrusted(X509Certificate[] certs, String authType) { }
      public void checkServerTrusted(X509Certificate[] certs, String authType) { }

    } };

    SSLContext sc;
    try {
      sc = SSLContext.getInstance("SSL");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    try {
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
    } catch (KeyManagementException e) {
      throw new RuntimeException(e);
    }
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

    // Create all-trusting host name verifier
    HostnameVerifier allHostsValid = new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) { return true; }
    };
    // Install the all-trusting host verifier
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
  }

  protected static Proxy determineProxy(String[] args) {
    Proxy proxy = null;
    String proxyHost = null, proxyAuthUser = null, proxyAuthPass = null;
    int proxyPort = -1;
    for (String arg : args) {
      if (arg.startsWith("--proxy=")) {
        String mArg = arg.substring("--proxy=".length());
        if (mArg.matches(".+:\\d+")) {
          String[] ss = mArg.split(":");
          proxyHost = ss[0];
          proxyPort = Integer.parseInt(ss[1]);
        } else {
          throw new IllegalArgumentException("expected: '--proxy=host:port', actual: '" + arg + "'");
        }
      } else if (arg.startsWith("--proxyAuth=")) {
        String mArg = arg.substring("--proxyAuth=".length());
        if (mArg.matches(".+:.+")) {
          String[] ss = mArg.split(":");
          proxyAuthUser = ss[0];
          proxyAuthPass = ss[1];
        } else {
          throw new IllegalArgumentException("expected: '--proxyAuth=login:password', actual: '" + arg + "'");
        }
      }
    }
    if (proxyHost != null) {
      proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
    if (proxyAuthUser != null) {
      String mProxyAuthUser = proxyAuthUser, mProxyAuthPass = proxyAuthPass;
      Authenticator authenticator = new Authenticator() {
        public PasswordAuthentication getPasswordAuthentication() {
          return (new PasswordAuthentication(mProxyAuthUser, mProxyAuthPass.toCharArray()));
        }
      };
      Authenticator.setDefault(authenticator);
    }
    return proxy;
  }

  protected static File determineOutFolder(String[] args) {
    String outFolder = null;
    for (String arg : args) {
      if (arg.startsWith("--outFolder=")) {
        outFolder = arg.substring("--outFolder=".length());
        break;
      }
    }
    if (outFolder == null) {
      throw new IllegalStateException();
    }
    return new File(outFolder);
  }

  protected static void prepareFolder(File folder) {
    if (folder.exists() && !folder.isDirectory()) {
      throw new IllegalArgumentException("outFolder is not a directory: " + folder.getAbsolutePath());
    } else if (!folder.exists()) {
      if (!folder.mkdirs()) {
        throw new IllegalArgumentException("Could not create outFolder directory: " + folder.getAbsolutePath());
      }
    }
  }

  protected static String determineStreamListUrl(String[] args) {
    String streamListUrl = null;
    for (String arg : args) {
      if (arg.startsWith("--streamListUrl=")) {
        streamListUrl = arg.substring("--streamListUrl=".length());
        break;
      }
    }
    return streamListUrl;
  }

  public static class PartLoadInfo {
    public String url;
    public String filename;
    public String loadStatus;
    public String loadError;
  }

  @Override
  public void run() {
    try {
      // stage 1: download stream list, select the stream with max resolution
      {
        if (!appLog.stage1_isCompleted()) {

          URL url = new URL(streamListUrl);
          List<String> lines = downloadAndReadLines(url); // see example in file response-stream-list.txt

          // find maximal extension
          String lineMaxResolution = lines.stream()
              .filter(line -> line.matches(".+RESOLUTION=\\d+x\\d+"))
              .max(Comparator.comparingInt(line -> Integer.parseInt(line.substring(line.lastIndexOf('x') + 1))))
              .orElseThrow(IllegalStateException::new);
          String streamUrl = lines.get(lines.indexOf(lineMaxResolution) + 1); // the next line

          appLog.stage1_setStreamListUrl(streamListUrl);
          appLog.stage1_setStreamUrl(streamUrl);
          appLog.stage1_setCompleted();
        }
      }

      // stage 2: download stream part urls
      {
        if (!appLog.stage2_isCompleted()) {

          String streamUrl = appLog.stage1_getStreamUrl();
          URL url = new URL(streamUrl);
          List<String> lines = downloadAndReadLines(url); // see example in file response-stream.txt

          List<PartLoadInfo> streamParts = lines.stream().filter(line -> {
            try {
              new URL(line);
              // valid URL
              return true;
            } catch (MalformedURLException e) {
              // invalid URL
              return false;
            }
          }).map(line -> {
            PartLoadInfo pli = new PartLoadInfo();
            pli.url = line;
            return pli;
          }).collect(Collectors.toList());

          appLog.stage2_setStreamParts(streamParts);
          appLog.stage2_setCompleted();
        }
      }

      // stage 3: download stream part contents
      {
        if (!appLog.stage3_isCompleted()) {
          out.println("Loading parts...");
          printLoadingProgress();

          boolean allLoaded = false;
          boolean loading = true;
          while (loading) {
            loading = false;

            boolean atLeastOneLoaded = false;
            List<PartLoadInfo> streamParts = appLog.stage3_getStreamParts();
            for (int i = 0; i < streamParts.size(); i++) {

              PartLoadInfo pli = streamParts.get(i);
              if (!"OK".equals(pli.loadStatus)) {
                loading = true;

                URL url = new URL(pli.url);
                try {
                  File file = downloadNextPart(url, i);
                  // download success
                  pli.filename = file.getName();
                  pli.loadStatus = "OK";
                  pli.loadError = null;

                  atLeastOneLoaded = true;

                  // update log
                  appLog.stage3_setStreamParts(streamParts);
                  printLoadingProgress();

                } catch (Throwable e) {
                  // download failure
                  pli.filename = null;
                  pli.loadStatus = "ERR";
                  StringWriter sw = new StringWriter();
                  e.printStackTrace(new PrintWriter(sw));

                  pli.loadError = sw.toString();

                  // update log
                  appLog.stage3_setStreamParts(streamParts);
                }
              }
            }

            if (!loading) {
              allLoaded = true;
            }

            if (loading && !atLeastOneLoaded) {
              loading = false;
              out.println("Loading stopped because of the repetitive errors, see log for details");
            }
          }

          if (allLoaded) {
            appLog.stage3_setCompleted();
            out.println("Loading completed!");
          }
        }
      }

      { // stage 4: prepare script for ffmpeg
        if (!appLog.stage4_isCompleted()) {
          List<String> partFilenames = appLog.stage3_getStreamParts().stream().map(pli -> pli.filename).collect(Collectors.toList());

          File script = new File(outFolder, "ffmpeg-list.txt");
          try (PrintStream ps = new PrintStream(new FileOutputStream(script), true)) {
            partFilenames.forEach(filename -> {
              String partFilenameAbs = new File(partsFolder, filename).getAbsolutePath();
              partFilenameAbs = partFilenameAbs.replaceAll("\\\\", "/");
              ps.println("file '" + partFilenameAbs + "'");
            });
          }

          appLog.stage4_setCompleted();
        }
      }

      out.println("SUCCESS");

    } catch (Throwable e) {
      appLog.error(e);
      throw new RuntimeException(e);
    }
  }

  protected void printLoadingProgress() {
    List<PartLoadInfo> streamParts = appLog.stage3_getStreamParts();
    int loaded = (int)streamParts.stream().filter(pli_ -> "OK".equals(pli_.loadStatus)).count();
    int total = streamParts.size();
    out.println("Loaded " + loaded + "/" + total + " parts");
  }

  protected List<String> downloadAndReadLines(URL url) {
    List<String> lines = new ArrayList<>();
    URLConnection conn = connector.apply(url);
    try (BufferedReader r = new BufferedReader(new InputStreamReader(new BufferedInputStream(conn.getInputStream())))) {
      String line;
      while ((line = r.readLine()) != null) {
        lines.add(line);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return lines;
  }

  /**
   * @return the downloaded file
   */
  protected File downloadNextPart(URL url, int i) {
    String filename = String.format("%05d", i) + ".m3u";
    File file = new File(partsFolder, filename);

    URLConnection connection = connector.apply(url);
    try (InputStream in = new BufferedInputStream(connection.getInputStream());
         OutputStream os = new FileOutputStream(file)) {
      byte[] dataBuffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
        os.write(dataBuffer, 0, bytesRead);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return file;
  }
}
