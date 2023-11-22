To download a videostream from a site:

1) Obtain the `.m3u8` file URL from the site using browser dev tools (filter by resource extension).

2) Download stream as a sequence of `.ts` file parts into a folder.  
Run with Java 11 on Windows:
"C:\work\jre-11\bin\java.exe" -jar m3u8-download-1.3.0.jar download --proxy=host:8080 --proxyAuth=login:password --m3u8=https://mysite.org/mystream.m3u8 --skip=0 --outFolder=C:\work\mystream-parts

Options:
download: action flag
--proxy (optional): proxy host and port to connect to the internet
--proxyAuth (optional): proxy login and password to connect to the internet
--m3u8: the `.m3u8` file URL
--skip (optional): skip a number of ts parts from the beginning (e.g. to continue downloading process)
--outFolder: a folder to store downloaded parts (will be created if does not exist)

3) List the downloaded `.ts` parts in a text file to further concatenate them into a video with ffmpeg tool.
Run with Java 11 on Windows: 
"C:\work\jre-11\bin\java.exe" -jar m3u8-download-1.3.0.jar ffmpeg-list --folder=C:\work\mystream-parts

Options:
ffmpeg-list: action flag
--folder: folder with downloaded parts
Output:
the `ffmpeg-list.txt` file in the folder specified

================================

Compile with Java 11:
mvn clean package


