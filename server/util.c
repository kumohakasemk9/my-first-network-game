#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <time.h>
#include <stdarg.h>
#include <string.h>
#include <math.h>
#include <pthread.h>
#include <arpa/inet.h>

#include "defs.h"
#include "util.h"
#include "data.h"

extern int Clients[];
extern character_t CHARA[];
extern int ClientUIDs[];
extern userinfo_t USERTABLE[];
extern char *MessageBuffer[];
extern int MessagesSendOnly[];
extern int GlobalMessagePointer;
extern int MessagePointer[];

void netlog(int id,const char* fmt, ...) {
	va_list varg;
	time_t rt = time(NULL);
	char tb[256];
	strftime(tb,sizeof(tb),"%T/%F",localtime(&rt));
	if(id != -1) {
		if(ClientUIDs[id] != -1) {
			printf("[\x1b[34m%s\x1b[0m][\x1b[32m%s\x1b[0m] ",tb,getusername(ClientUIDs[id]));
		} else {
			printf("[\x1b[34m%s\x1b[0m][\x1b[33m%d\x1b[0m] ",tb,id);
		}
	} else {
		printf("[\x1b[34m%s\x1b[0m]",tb);
	}
	va_start(varg,fmt);
	vprintf(fmt,varg);
	va_end(varg);
}

int readuint8(int sock) {
	uint8_t r;
	int i;
	i = read(sock,&r,1);
	if(i != 1) {return -1;}
	return r;
}

int readdata(int sock,void *data,int len) {
	int i = 0;
	uint8_t *d = data;
	while(i < len) {
		int r = read(sock,d + i,len - i);
		if(r < 1) {return -1;}
		i += r;
	}
	return i;
}

void putuint16(void* buf,int off,uint16_t data) {
	uint8_t *p = buf;
	p[off] = data >> 8;
	p[off + 1] = data & 0xff;
}

int isCharaActive(int id) {
	if(id != -1 && (CHARA[id].localflag & CHARA_ACTIVE) != 0) {return 1;}
	return 0;
}

void PutMessage(int sendonly,const char *ptn, ...) {
	char Buffer[255];
	va_list varg;
	int a,p = 0;
	va_start(varg,ptn);
	a = vsnprintf(Buffer,sizeof(Buffer),ptn,varg) + 1;
	va_end(varg);
	if(a > 255) {
		printf("Message was too long.\n");
		return;
	}
	PutBinaryData(sendonly,Buffer,a);
}

int RectanglarHitDetect(int x1,int y1,int w1,int h1,int x2,int y2,int w2,int h2) {
	//Detect two rectangles are overlapping or not (x1,y1,w1,h1) and (x1,y1,w1,h1)
	//       A                  B C     D
	//       |------------------| <=====> No detect
	//       |---------------<=====>      Detect C
	//       |------<=====>-----|         Detect C D
	//    <=====>---------------|         Detect D
	//<=====>|==================|         No detect
	int xdetect = 0;
	int ydetect = 0;
	int xx1 = x1 + w1 - 1;
	int yy1 = y1 + h1 - 1;
	int xx2 = x2 + w2 - 1;
	int yy2 = y2 + h2 - 1;
	//Detect for X axis
	if(xx1 < xx2) {
		//x2 ... B-A, x1 ... D-C
		if(x2 <= x1 && x1 <= xx2) {xdetect = 1;}
		if(x2 <= xx1 && xx1 <= xx2) {xdetect = 1;}
	} else {
		//x2 ... D-C, x1 ... B-A
		if(x1 <= x2 && x2 <= xx1) {xdetect = 1;}
		if(x1 <= xx2 && xx2 <= xx1) {xdetect = 1;}
	}
	//Detect for Y axis
	if(yy1 < yy2) {
		if(y2 <= y1 && y1 <= yy2) {ydetect = 1;}
		if(y2 <= yy1 && yy1 <= yy2) {ydetect = 1;}
	} else {
		if(y1 <= y2 && y2 <= yy1) {ydetect = 1;}
		if(y1 <= yy2 && yy2 <= yy1) {ydetect = 1;}
	}
	if(xdetect == 1 && ydetect == 1) {return 0;}
	return -1;
}

int authorize(char* pwd) {
	for(int i = 0;i < MAX_USERS; i++) {
		if(strcmp(USERTABLE[i].pwd,pwd) == 0) {
			return i;
		}
	}
	return -1;
}

char* getusername(int id) {return USERTABLE[id].uname;}

int AddCharacter(double x,double y,int w,int h,int imgid) {
	int i;
	//pthread_mutex_lock(&CharacterMutex); //Avoid multiple thread accesses same id
	for(i = 0;i < MAX_CHARAS; i++) {
		if(!isCharaActive(i)) {
			memset(&CHARA[i],0,sizeof(character_t));
			CHARA[i].x = x;
			CHARA[i].y = y;
			CHARA[i].w = w;
			CHARA[i].h = h;
			CHARA[i].imageid = imgid;
			CHARA[i].tx = -1;
			CHARA[i].ty = -1;
			CHARA[i].localflag = CHARA_ACTIVE;
			MarkCharacterAltered(i);
			return i;
		}
	}
	//pthread_mutex_unlock(&CharacterMutex); //Critical section end
	if(i >= MAX_CHARAS) {return -1;}
}

void MarkCharacterAltered(int id) {
	for(int i = 0;i < MAX_CONNECTIONS; i++) {CHARA[id].updateflag[i] = 1;}
}

void RemoveCharacter(int id) {
	CHARA[id].localflag &= ~CHARA_ACTIVE;
	MarkCharacterAltered(id);
}

int CalcAngleToTarget(double sx,double sy,double dx,double dy) {
	int r = atan(fabs(dy - sy)/fabs(dx - sx)) * 180.0 / M_PI;
	if(dx > sx) {r = 180 - r;}
	if(dy <= sy) {r = 360 - r;}
	return r;
}

double FixCoordinateToTarget(double sx,double sy,double *dx,double *dy,double len) {
	double r = atan2(*dy - sy,*dx - sx);
	*dx = len * cos(r);
	*dy = len * sin(r);
	return r;
}

double limit(double v,double l,double u) {
	if(v < l) {return l;}
	else if(v > u) {return u;}
	return v;
}

int WillOverflow(double c,double n,double l) {
	if(n > 0 && c + n > l) {return 1;}
	if(n < 0 && c + n < l) {return 1;}
	return 0;
}

void PutBinaryData(int id,char* buffer,int len) {
	char* t = (char*)malloc(len);
	if(t == NULL) {
		printf("Error on malloc().\n");
		return;
	}
	memcpy(t,buffer,len);
	//Detect buffer full
	for(int i = 0;i < MAX_CONNECTIONS; i++) {
		if(Clients[i] != -1) {
			int p = MessagePointer[i];
			int usage;
			if(p < GlobalMessagePointer) {
				//   |p---------->|GlobalMessagePointer
				//0---------------------------------->buffer max(MAX_MSGBUF - 1)
				usage = GlobalMessagePointer - p;
			} else if(p > GlobalMessagePointer) {
				//--->|GlobalMessagePointer    |p----
				//---------------------------------->buffer max
				usage = MAX_MSGBUF - p + GlobalMessagePointer;
			} else {continue;}
			if(usage >= MAX_MSGBUF - 2) {
				printf("MessageBuffer is full.\n");
				return;
			}
		}
	}
	//pthread_mutex_lock(&MessageBufferMutex); //Avoid multiple access: if line 139 is executed after line 140, this will make weird result.
	if(MessageBuffer[GlobalMessagePointer] != NULL) {free(MessageBuffer[GlobalMessagePointer]);}
	MessageBuffer[GlobalMessagePointer] = t;
	MessagesSendOnly[GlobalMessagePointer] = id;
	if(GlobalMessagePointer < MAX_MSGBUF - 1) {GlobalMessagePointer++;} else {GlobalMessagePointer = 0;}
	//pthread_mutex_unlock(&MessageBufferMutex); //Critical Section End
}
