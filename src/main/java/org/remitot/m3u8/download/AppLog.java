package org.remitot.m3u8.download;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class AppLog {

  protected final File logFile;

  public AppLog(File logFile) {
    this.logFile = logFile;
    prepareLogFile();
    read();
  }

  protected void prepareLogFile() {
    if (logFile.exists() && logFile.isDirectory()) {
      throw new IllegalArgumentException("logFile is a directory: " + logFile.getAbsolutePath());
    } else if (!logFile.exists()) {
      try {
        if (!logFile.createNewFile()) {
          throw new IllegalArgumentException("Could not create logFile: " + logFile.getAbsolutePath());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    if (!logFile.canRead()) {
      throw new IllegalArgumentException("Could not read logFile: " + logFile.getAbsolutePath());
    }
    if (!logFile.canWrite()) {
      throw new IllegalArgumentException("Could not write logFile: " + logFile.getAbsolutePath());
    }
  }

  protected void write() {
    try (FileWriter fw = new FileWriter(logFile, false)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(map, fw);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void read() {
    try (FileReader fr = new FileReader(logFile)) {
      map = new Gson().fromJson(fr, new TypeToken<TreeMap<String, Object>>(){}.getType());
      if (map == null) {
        map = new TreeMap<>();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected TreeMap<String, Object> map;



  // public API //
  public void error(Throwable e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    map.put("error", sw.toString());
    write();
  }
  public void stage1_setStreamListUrl(String value) {
    map.put("stage1.1.streamListUrl", value);
    write();
  }
  public String stage1_getStreamUrl() {
    return (String)map.get("stage1.2.streamUrl");
  }
  public void stage1_setStreamUrl(String value) {
    map.put("stage1.2.streamUrl", value);
    write();
  }
  public boolean stage1_isCompleted() {
    return "yes".equals(map.get("stage1.3.completed"));
  }
  public void stage1_setCompleted() {
    map.put("stage1.3.completed", "yes");
    write();
  }
  public boolean stage2_isCompleted() {
    return "yes".equals(map.get("stage2.1.completed"));
  }
  public void stage2_setStreamParts(List<Processor.PartLoadInfo> value) {
    stage3_setStreamParts(value);
  }
  public void stage2_setCompleted() {
    map.put("stage2.1.completed", "yes");
    write();
  }
  public boolean stage3_isCompleted() {
    return "yes".equals(map.get("stage3.2.completed"));
  }
  public void stage3_setCompleted() {
    map.put("stage3.2.completed", "yes");
    write();
  }
  public List<Processor.PartLoadInfo> stage3_getStreamParts() {
    Object streamParts = map.get("stage3.1.streamParts");
    if (streamParts == null) {
      return new ArrayList<>();
    } else {
      if (streamParts instanceof List) {
        List<?> streamPartsList = (List<?>)streamParts;
        List<Processor.PartLoadInfo> pliList = new ArrayList<>();
        for (int i = 0; i < streamPartsList.size(); i++) {
          Object streamPart = streamPartsList.get(i);
          if (streamPart instanceof Map) {
            Map<String, String> streamPartMap = (Map<String, String>) streamPart;
            Processor.PartLoadInfo pli = new Processor.PartLoadInfo();
            pli.url = streamPartMap.get("url");
            pli.filename = streamPartMap.get("filename");
            pli.loadStatus = streamPartMap.get("loadStatus");
            pli.loadError = streamPartMap.get("loadError");
            pliList.add(pli);
          } else{
            throw new IllegalStateException("3.streamParts[" + i + "] object is " + streamParts.getClass().getCanonicalName());
          }
        }
        return pliList;
      } else {
        throw new IllegalStateException("3.streamParts object is " + streamParts.getClass().getCanonicalName());
      }
    }
  }
  public void stage3_setStreamParts(List<Processor.PartLoadInfo> value) {
    List<?> list = value == null ? null : value.stream().map(pli -> {
      Map<String, String> map = new LinkedHashMap<>();
      map.put("url", pli.url);
      map.put("filename", pli.filename);
      map.put("loadStatus", pli.loadStatus);
      map.put("loadError", pli.loadError);
      return map;
    }).collect(Collectors.toList());

    map.put("stage3.1.streamParts", list);
    write();
  }
  public boolean stage4_isCompleted() {
    return "yes".equals(map.get("stage4.1.completed"));
  }
  public void stage4_setCompleted() {
    map.put("stage4.1.completed", "yes");
    write();
  }
}
