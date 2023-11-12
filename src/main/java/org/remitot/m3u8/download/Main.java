package org.remitot.m3u8.download;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Main {

  protected static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
  
  public static void main(String[] args) {
    if (args == null) {
      args = new String[0];
    }

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

    String filenamePrefix = "part-"; // TODO read from args

    Downloader downloader = new Downloader(outFolder, filenamePrefix, connector);
    
    for (int i = skip; i < tsUrlStrs.size(); i++) {
      String tsUrlStr = tsUrlStrs.get(i);

      URL tsUrl = null;
      try {
        tsUrl = new URL(tsUrlStr);
      } catch (MalformedURLException e) {
        throw new RuntimeException(e); // impossible
      }

      try {
        downloader.downloadNextTs(tsUrl, i);
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
}
