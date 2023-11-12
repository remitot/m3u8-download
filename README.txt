Downloads m3u8 stream as `.ts` parts into a folder.

Run with Java 11 on Windows:
"C:\work\jre-11\bin\java.exe" -jar m3u8-download-1.0.0.jar --proxy=host:8080 --proxyAuth=login:password --m3u8=https://mysite.org/mystream.m3u8 --skip=0 --outFolder=C:\work\mystream-parts

Options:
--proxy (optional): proxy host and port to connect to the internet
--proxyAuth (optional): proxy login and password to connect to the internet
--m3u8: the URL to download m3u8 from
--skip (optional): skip a number of ts parts from the beginning
--outFolder: folder to store downloaded parts (will be created if does not exist)

Compile with Java 11:
mvn clean package


