JAVA := java
JAVAC := javac

all:
	$(javac) Client.java
	$(javac) SocketServer.java

client:
	$(JAVA) Client.java

server:
	%(JAVA) SocketServer.java
