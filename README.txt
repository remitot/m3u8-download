To download a videostream from a site:

1) Obtain the `.m3u8` file URL from the site using browser dev tools (filter by resource extension).

2) Download stream as `.ts` file parts into a folder.  
Run with Java 11 on Windows:
"C:\work\jre-11\bin\java.exe" -jar m3u8-download-1.1.0.jar download --proxy=host:8080 --proxyAuth=login:password --m3u8=https://mysite.org/mystream.m3u8 --skip=0 --outFolder=C:\work\mystream-parts

Options:
download: action flag
--proxy (optional): proxy host and port to connect to the internet
--proxyAuth (optional): proxy login and password to connect to the internet
--m3u8: the `.m3u8` file URL
--skip (optional): skip a number of ts parts from the beginning
--outFolder: a folder to store downloaded parts (will be created if does not exist)

3) Group the downloaded `.ts` parts into few lists to further concatenate them into large video chunks (with ffmpeg tool).
Run with Java 11 on Windows: 
"C:\work\jre-11\bin\java.exe" -jar m3u8-download-1.1.0.jar ffmpeg-list --size=1024 --folder=C:\work\mystream-parts

Options:
ffmpeg-list: action flag
--size (optional): average size of each larger video chunk after concatenation, in MB
--folder: folder with downloaded parts (the list files will be created there too)


Compile with Java 11:
mvn clean package


