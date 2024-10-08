Чтобы скачать видеопоток в формате m3u8 с сайта, нужно:

1) Исследовать сайт: обнаружить URL, с которого сайт посредством JS-запроса получает список различных потоков такого вида:
```
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=768000,CODECS="mp4a.40.2, avc1.64001e",RESOLUTION=640x360
https://server.com/playlist/media/f9848286302057ab8813f768e54a621b/79b8d2b8db12944494c43f8335a1f7b0/360?consumer=vod&sid=&user-cdn=cdnvideo&version=17%3A2%3A1%3A0%3Acdnvideo&user-id=300840080&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjozMDA4NDAwODB9.SLnHJRTJX5fl8CsbYKB0euvXzsKu4UqYPIQ8S8XdZ7g
#EXT-X-STREAM-INF:BANDWIDTH=768000,CODECS="mp4a.40.2, avc1.64001e",RESOLUTION=640x360
https://server.com/playlist/media/f9848286302057ab8813f768e54a621b/79b8d2b8db12944494c43f8335a1f7b0/360?consumer=vod&sid=&user-cdn=integrosproxy&version=17%3A2%3A1%3A0%3Aintegrosproxy&user-id=300840080&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjozMDA4NDAwODB9.SLnHJRTJX5fl8CsbYKB0euvXzsKu4UqYPIQ8S8XdZ7g
...
#EXT-X-STREAM-INF:BANDWIDTH=4096000,CODECS="mp4a.40.2, avc1.64001e",RESOLUTION=1920x1080
https://server.com/playlist/media/f9848286302057ab8813f768e54a621b/79b8d2b8db12944494c43f8335a1f7b0/1080?consumer=vod&sid=&user-cdn=gcore&version=17%3A2%3A1%3A0%3Agcore&user-id=300840080&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjozMDA4NDAwODB9.SLnHJRTJX5fl8CsbYKB0euvXzsKu4UqYPIQ8S8XdZ7g
#EXT-X-STREAM-INF:BANDWIDTH=4096000,CODECS="mp4a.40.2, avc1.64001e",RESOLUTION=1920x1080
https://server.com/playlist/media/f9848286302057ab8813f768e54a621b/79b8d2b8db12944494c43f8335a1f7b0/1080?consumer=vod&sid=&user-cdn=cloudflare&version=17%3A2%3A1%3A0%3Acloudflare&user-id=300840080&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjozMDA4NDAwODB9.SLnHJRTJX5fl8CsbYKB0euvXzsKu4UqYPIQ8S8XdZ7g
```
(сайт делает этот запрос после нажатия пользователем кнопки Play и перед началом воспроизведения видео).

2) Запустить программу из командной строки, передав в качестве параметров:
   --proxy (optional): proxy host and port to connect to the internet
   --proxyAuth (optional): proxy login and password to connect to the internet
   --outFolder: рабочая папка для размещения временных файлов и итогового видео (во избежание перезаписи информации лучше указать несуществующий путь, по которому автоматически будет создана папка)
   --streamListUrl: URL загрузки списка потоков, обнаруженный в пункте 1)
Пример:
> "C:\work\jre-11\bin\java.exe" -jar m3u8-download-2.0.0.jar --outFolder=D:\my-movie --streamListUrl=https://player.com/video/my-movie --proxy=host:8080 --proxyAuth=login:password

3) После успешного завершения программы текстом SUCCESS в папке outFolder появится файл ffmpeg-list.exe, с помощью которго нужно выполнить команду склейки итогового видео утилитой `ffmpeg-6.0`.
Пример:
> "C:\Program Files\ffmpeg-6.0\bin\ffmpeg.exe" -f concat -safe 0 -i "D:\my-movie\ffmpeg-list.txt" -c copy "D:\my-movie\my-movie.mp4"

* Если программа не завершилась текстом SUCCESS, а завершилась ошибкой или зависла, 
то её нужно запустить повторной командой (без изменений), при этом выполнение продолжится с места прерывания. 
Это происходит потому что программа сохраняет прогресс выполнения и логи в файле `app-log.txt`.
Чтобы полностью перезапустить процесс скачивания видео с начала, достаточно удалить файл `app-log.txt`.

4) После получения итогового видео `D:\my-movie\my-movie.mp4`, его можно разместить в любой другой локации, а рабочую папку outFolder вместе со всем содержимым удалить.

================================

Compile with Java 11:
mvn clean package


