BUDOWANIE (bez instalowania Android Studio / .NET):
1. Załóż puste repozytorium na github.com (np. "phonecast").
2. W folderze PhoneCast uruchom:
   git init && git add . && git commit -m init
   git branch -M main
   git remote add origin https://github.com/TWOJ_LOGIN/phonecast.git
   git push -u origin main
3. Zakładka Actions -> "Build PhoneCast" -> po ok. 5 min na dole strony
   pobierz PhoneCast-apk (app-debug.apk) i PhoneCast-exe (PhoneCast.exe).
