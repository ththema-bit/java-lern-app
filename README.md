# java-lern-app

A desktop app (Java Swing) for learning Java and preparing for junior interviews. The content
(lessons, quizzes, exercises, cheat sheet) is in German.

## Features

- Lessons by chapter, each with an explanation, a task and a code editor
- Built-in compile and run (uses the JDK's compiler at runtime), with a step debugger and a mock interview mode
- Quizzes, flash-style questions, debugging exercises, workout tasks, small projects (e.g. a bank app)
- Dashboard with progress, handwritten-notes panel, reference lookup
- Self test that compiles and checks every sample solution

## Requirements

A JDK 17 or newer (not just a JRE), because the app compiles your code while it runs.

## Run

```bash
java -jar JavaLernApp.jar
```

## Build from source

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/lernapp/*.java
cp src/resources/* out/
printf 'Main-Class: lernapp.JavaLernApp\n' > manifest.txt
jar cfm JavaLernApp.jar manifest.txt -C out .
```

Check all lessons and solutions:

```bash
java -jar JavaLernApp.jar --selftest
```

Note: the committed JAR was built before the sample names in two resource files were changed;
the two text resources inside it were replaced with the current ones, but the classes were not
recompiled. Rebuild with the commands above if you want a clean build.

## Kurzfassung (Deutsch)

Lern-App für Java-Einsteiger und Vorstellungsgespräche: Lektionen, Quiz, Übungen, Mini-Projekte,
Code-Editor mit Kompilieren und Ausführen. Starten mit `java -jar JavaLernApp.jar` (JDK nötig).

## License

MIT
