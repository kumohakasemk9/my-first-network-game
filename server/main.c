
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <poll.h>
#include <string.h>
#include <time.h>
#include <math.h>

#include "defs.h"
#include "data.h"
#include "util.h"
#include "netproc.h"

void gameTick();
character_t CHARA[MAX_CHARAS];
int CloseServer = 0;
int Clients[MAX_CONNECTIONS];
extern char *MessageBuffer[];
int ClientUIDs[MAX_CONNECTIONS];
extern int PlayableIDs[];
extern const userinfo_t USERTABLE[];
extern int ClientPing[];
FILE *action_log;

void SIGINT_handler(int);
void UserDataHandler(int,char*);
void QHandler(int,int,int);
void WHandler(int,int,int);
void EHandler(int,int,int);
void RHandler(int,int,int);
void MapClickHandler(int,int,int);

void main(int argc,char *argv[]) {
	int sock,i;
	struct sockaddr_in addr;
	struct pollfd fds;
	action_log = fopen("actions","a");
	if(action_log == NULL) {
		printf("log file open failed.\n");
		return;
	}
	//Init arrays
	for(i = 0;i < MAX_CONNECTIONS;i++) {Clients[i] = -1;}
	for(i = 0; i < MAX_CHARAS;i++) {CHARA[i].localflag = 0;}
	for(i = 0;i < MAX_MSGBUF;i++) {MessageBuffer[i] = NULL;}
	signal(SIGINT,SIGINT_handler);
	signal(SIGPIPE,SIG_IGN);
	signal(SIGFPE,SIG_IGN);
	addr.sin_port = htons(15000);
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = INADDR_ANY;
	if(argc >= 2) {
		int t = atoi(argv[1]);
		if(1 <= t && t <= 65535) {
			addr.sin_port = htons(t);
		} else {
			printf("Port number out of order.\n");
		}
	}
	sock = socket(AF_INET,SOCK_STREAM,0);
	if(sock == -1) {
		printf("Socket failed.\n");
		return;
	}
	fds.fd = sock;
	fds.events = POLLIN;
	if(bind(sock,(struct sockaddr*)&addr,sizeof(addr)) == -1) {
		printf("Bind failed.\n");
		close(sock);
		return;
	}
	if(listen(sock,1) == -1) {
		printf("Listen failed.\n");
		close(sock);
		return;
	}
	printf("Server started at *:%d.\n",ntohs(addr.sin_port));
	while(CloseServer == 0) {
		if(poll(&fds,1,10) == 1) {NewClientHandler(sock);} //Accept new clients if available
		gameTick();	
	}
	printf("Server is closing now.\n");
	close(sock);
	fclose(action_log);
}

void SIGINT_handler(int s) {CloseServer = 1;}

void gameTick() {
	//GameTick, called every 10mS
	for(int i = 0;i < MAX_CHARAS; i++) {
		if(isCharaActive(i)) {
			int f = 0;
			//BUGGY: in some condition, character got glued
			//Stop at target pos
			if(CHARA[i].tx != -1 && WillOverflow(CHARA[i].x,CHARA[i].sx,CHARA[i].tx)) {
				CHARA[i].x = CHARA[i].tx;
				CHARA[i].tx = -1;
				CHARA[i].sx = 0;
				f = 1; //Update
			}
			if(CHARA[i].ty != -1 && WillOverflow(CHARA[i].y,CHARA[i].sy,CHARA[i].ty)) {
				CHARA[i].y = CHARA[i].ty;
				CHARA[i].ty = -1;
				CHARA[i].sy = 0;
				f = 1; //Update
			}
			if(CHARA[i].sx != 0) {
				f = 1; //Update
				CHARA[i].x = limit(CHARA[i].x + CHARA[i].sx,0,MAP_LIMIT_X);
			}
			if(CHARA[i].sy != 0) {
				f = 1; //Update
				CHARA[i].y = limit(CHARA[i].y + CHARA[i].sy,0,MAP_LIMIT_Y);
			}
			if(f == 1) {MarkCharacterAltered(i);}
			if(CHARA[i].Qcd != 0) {CHARA[i].Qcd--;}
			if(CHARA[i].Wcd != 0) {CHARA[i].Wcd--;}
			if(CHARA[i].Ecd != 0) {CHARA[i].Ecd--;}
			if(CHARA[i].Rcd != 0) {CHARA[i].Rcd--;}
		}
	}
}

void UserDataHandler(int id,char* buffer) {
	if(ClientUIDs[id] == -1) {
		//No UID, then authorization
		int i;
		i = authorize(buffer);
		if(i == -1) {
			netlog(id,"Wrong password.\n");
			PutMessage(id,"Wrong word.");
		} else {
			int j;
			for(j = 0;j < MAX_CONNECTIONS;j++) {
				if(Clients[j] != -1 && ClientUIDs[j] == i) {
					break;
				}
			}
			if(j < MAX_CONNECTIONS) {
				//If there is already the client that has same UID
				netlog(id,"Attempted to log in into the user that already logged in.\n");
				PutMessage(id,"Already logged in.");
			} else {
				//Auth completed
				int j = AddCharacter(100,100,100,100,USERTABLE[i].imageid);
				if(j != -1) {
					ClientUIDs[id] = i;
					PlayableIDs[id] = j;
					netlog(id,"Logged in. id=%d\n",id);
					PutMessage(id,"Hello, %s.",getusername(i));
					PutMessage(-1,"\x01%d\x01%s",j,getusername(i));
					PutMessage(id,"\x4%d",PlayableIDs[id]);
				} else {
					netlog(id,"Login cancelled because CHARA[] is full.\n");
					PutMessage(id,"Could not login, try it later.");
				}
			}
		}
	} else {
		if(strcmp(buffer,"!ping") == 0) {
			if(ClientPing[id] != -1) {PutMessage(id,"Server data sync time=%lf[mS]",((double)ClientPing[id] / (double)CLOCKS_PER_SEC) * 1000);}
		} else {
			netlog(id,"\x1b[4m%s\x1b[0m\n",buffer);
			PutMessage(-1,"[%s] %s",getusername(ClientUIDs[id]),buffer);
		}
	}
}

void QHandler(int id,int x,int y) {
	if(PlayableIDs[id] == -1) {CHARA[PlayableIDs[id]].Qcd = 100;}
}

void WHandler(int id,int x,int y) {
	if(PlayableIDs[id] == -1) {CHARA[PlayableIDs[id]].Wcd = 100;}
}

void EHandler(int id,int x,int y) {
	if(PlayableIDs[id] == -1) {CHARA[PlayableIDs[id]].Ecd = 100;}
}

void RHandler(int id,int x,int y) {
	if(PlayableIDs[id] == -1) {CHARA[PlayableIDs[id]].Rcd = 100;}
}

void MapClickHandler(int id,int x,int y) {
	fprintf(action_log,"[%d] %d,%d\n",id,x,y);
	fflush(action_log);
	if(PlayableIDs[id] != -1) {
		int cid = PlayableIDs[id];
		double sx = x;
		double sy = y;
		int d = FixCoordinateToTarget(CHARA[cid].x,CHARA[cid].y,&sx,&sy,3.0) / M_PI * 180.0;
		if(-90 <= d && d <= 90) {
			CHARA[cid].statusflag |= CHARA_INVERT;
		} else {
			CHARA[cid].statusflag &= ~CHARA_INVERT;
			if(d > 0) {d = 180 - d;} else {d = -(180 + d);}
		}
		CHARA[cid].rotate = d;
		CHARA[cid].sx = sx;
		CHARA[cid].sy = sy;
		CHARA[cid].tx = x;
		CHARA[cid].ty = y;
		MarkCharacterAltered(cid);
	}
}
