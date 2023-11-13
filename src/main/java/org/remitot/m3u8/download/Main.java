package org.remitot.m3u8.download;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Main {

  protected static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
  
  public static void main(String[] args) {
    boolean action = false;
    if (args != null && args.length > 0) {
      if ("download".equals(args[0])) {
        download(args);
        action = true;
      } else if ("ffmpeg-list".equals(args[0])) {
        ffmpegList(args);
        action = true;
      }
    }
    if (!action) {
      System.out.println("Unknown action, exit");
    }
  }
  
  protected static final Function<Integer, String> partFilenameByIndex = index -> "part-" + String.format("%05d", index) + ".ts";
  
  protected static void download(String[] args) {
    Proxy proxy = defineProxy(args);

    applyPkixWorkaround();

    URL m3u8url = defineM3u8url(args);
    File outFolder = defineOutFolder(args);


    Function<URL, URLConnection> connector = url -> {
      try {
        return proxy == null ? url.openConnection() : url.openConnection(proxy);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
    

    List<String> tsUrlStrs = new ArrayList<>();
    URLConnection m3u8conn = connector.apply(m3u8url);
    try (BufferedReader r = new BufferedReader(new InputStreamReader(new BufferedInputStream(m3u8conn.getInputStream())))) {
      String line;
      while ((line = r.readLine()) != null) {
        try {
          new URL(line);
          tsUrlStrs.add(line);
        } catch (MalformedURLException e) {
          // not a valid url, skip line
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }


    int skip = defineSkip(args);
    if (skip > 0) {
      System.out.println("Skipped " + skip + " parts");
    }

    for (int i = skip; i < tsUrlStrs.size(); i++) {
      String tsUrlStr = tsUrlStrs.get(i);

      URL tsUrl = null;
      try {
        tsUrl = new URL(tsUrlStr);
      } catch (MalformedURLException e) {
        throw new RuntimeException(e); // impossible
      }

      try {
        URLConnection connection = connector.apply(tsUrl);
        downloadNextTs(connection, i, outFolder);
      } catch (Throwable e) {
        if (i > 0) {
          System.out.println("Error downloading part #" + i);
        } else {
          System.out.println("Error downloading part #" + i + ", for the next launch set --skip=" + i);
        }
        throw e;
      }

      System.out.println("Successfully downloaded part #" + i + ", remained " + (tsUrlStrs.size() - i - 1) + " parts");
    }
  }
    

  protected static File defineOutFolder(String[] cmdArgs) {
    for (String arg : cmdArgs) {
      if (arg.startsWith("--outFolder=")) {
        String mArg = arg.substring("--outFolder=".length());
        File f = new File(mArg);
        if (!f.exists()) {
          if (!f.mkdirs()) {
            throw new IllegalStateException("Could not create dir: " + f);
          }
        }
        if (!f.isDirectory()) {
          throw new IllegalArgumentException("'" + mArg + "' is not a directory");
        }
        return f;
      }
    }
    throw new IllegalStateException("'--outFolder' argument is mandatory");
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

  protected static Proxy defineProxy(String[] cmdArgs) {
    Proxy proxy = null;
    String proxyHost = null, proxyAuthUser = null, proxyAuthPass = null;
    int proxyPort = -1;
    for (String arg : cmdArgs) {
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
  
  protected static URL defineM3u8url(String[] cmdArgs) {
    for (String arg : cmdArgs) {
      if (arg.startsWith("--m3u8=")) {
        String mArg = arg.substring("--m3u8=".length());
        try {
          return new URL(mArg);
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException("Malformed m3u8 URL", e);
        }
      }
    }
    throw new IllegalStateException("'--m3u8' argument is mandatory");
  }

  protected static int defineSkip(String[] cmdArgs) {
    for (String arg : cmdArgs) {
      if (arg.startsWith("--skip=")) {
        String mArg = arg.substring("--skip=".length());
        if (mArg.matches("\\d+")) {
          return Integer.parseInt(mArg);
        } else {
          throw new IllegalArgumentException("expected: '--skip=123', actual: '" + arg + "'");
        }
      }
    }
    return 0;
  }
  
  protected static void ffmpegList(String[] args) {
    File folder = defineFolder(args);
    
    // verify sequence of parts
    String[] partFilenamesArray = folder.list((dir, name) -> name.matches("part\\-\\d{5}\\.ts"));
    if (partFilenamesArray == null || partFilenamesArray.length == 0) {
      // do nothing
      System.out.println("The folder contains no 'part-*.ts' files");
    } else {
      
      List<String> partFilenames = new ArrayList<>(Arrays.asList(partFilenamesArray));

      File listFile = new File(folder, "ffmpeg-list.txt");
      try (PrintStream ps = new PrintStream(new FileOutputStream(listFile), true)) {
        for (int i = 0; i < partFilenames.size(); i++) {
          String partFilename = partFilenameByIndex.apply(i);
          if (!partFilenames.contains(partFilename)) {
            throw new IllegalStateException("Missing part: " + partFilename);
          }
          
          String partFilenameAbs = new File(folder, partFilename).getAbsolutePath();
          String partFilenameAbs2 = partFilenameAbs.replaceAll("\\\\", "/");
          ps.println("file '" + partFilenameAbs2 + "'");
        }
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected static File defineFolder(String[] cmdArgs) {
    for (String arg : cmdArgs) {
      if (arg.startsWith("--folder=")) {
        String mArg = arg.substring("--folder=".length());
        File f = new File(mArg);
        if (!f.exists()) {
          throw new IllegalArgumentException("'" + mArg + "' does not exist");
        }
        if (!f.isDirectory()) {
          throw new IllegalArgumentException("'" + mArg + "' is not a directory");
        }
        return f;
      }
    }
    throw new IllegalStateException("'--folder' argument is mandatory");
  }

  protected static void downloadNextTs(URLConnection connection, int i, File outFolder) {
    String filename = partFilenameByIndex.apply(i);
    File file = new File(outFolder, filename);

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
  }
}
