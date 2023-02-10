TARGETS= util.o main.o netproc.o data.o
CFLAGS=-g3

all: $(TARGETS)
	gcc $^ -o server -lpthread -lm

clean:
	rm $(TARGETS) server
