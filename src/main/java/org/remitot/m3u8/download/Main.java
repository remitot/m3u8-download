package org.remitot.m3u8.download;

public class Main {
  public static void main(String[] args) {
    System.out.print("Hello!");
    if (args != null) {
      for (String s: args) {
        System.out.print(" " + s);
      }
    }
    System.out.println();
  }
}
