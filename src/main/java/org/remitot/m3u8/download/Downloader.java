package org.remitot.m3u8.download;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;

public class Downloader {

  protected final File outFolder;
  protected final Function<URL, URLConnection> connector;
  protected final Function<Integer, String> partFilenameByIndex;
  
  public Downloader(File outFolder, Function<URL, URLConnection> connector, Function<Integer, String> partFilenameByIndex) {
    this.outFolder = outFolder;
    this.connector = connector;
    this.partFilenameByIndex = partFilenameByIndex;
  }
  
  public static class DefaultConnector implements Function<URL, URLConnection> {
    @Override
    public URLConnection apply(URL url) {
      try {
        return url.openConnection();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  } 
  
  public void downloadNextTs(URL ts, int i) {
    String filename = partFilenameByIndex.apply(i);
    File file = new File(outFolder, filename);

    try (InputStream in = new BufferedInputStream(connector.apply(ts).getInputStream());
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

