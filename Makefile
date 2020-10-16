JFLAGS=-g
JC=javac
JVM= java
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java
CLASSES = \
	Main.java
MAIN= Main
default: classes
classes: $(CLASSES:.java=.class)	
test: Main.class
	java Main test.in
clean: 
	rm test.out	
