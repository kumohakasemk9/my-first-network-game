#include <stdio.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include <string.h>
#include <errno.h>
#include <time.h>

#include "defs.h"
#include "data.h"
#include "util.h"
#include "netproc.h"

extern int Clients[];
int PlayableIDs[MAX_CONNECTIONS];
extern int CloseServer;
extern character_t CHARA[];
int MessagesSendOnly[MAX_MSGBUF];
int MessagePointer[MAX_CONNECTIONS];
int ClientPing[MAX_CONNECTIONS];
char *MessageBuffer[MAX_MSGBUF];
int GlobalMessagePointer = 0;
int FirstDataSend[MAX_CONNECTIONS];
extern int ClientUIDs[];

extern void QHandler(int,int,int);
extern void WHandler(int,int,int);
extern void EHandler(int,int,int);
extern void RHandler(int,int,int);
extern void MapClickHandler(int,int,int);

void NewClientHandler(int sock) {
	struct timeval t;
	struct sockaddr_in addr;
	int i = sizeof(addr);
	int cli = accept(sock,(struct sockaddr*)&addr,(unsigned int*)&i);
	pthread_t p;
	printf("%s is accept()ed.\n",inet_ntoa(addr.sin_addr));
	t.tv_sec = 5;
	t.tv_usec = 0;
	//Find empty slot
	for(i = 0;i < MAX_CONNECTIONS;i++) {
		if(Clients[i] == -1) {break;}
	}
	if(i >= MAX_CONNECTIONS) {
		//No more slot
		write(cli,"E\x0cServer busy.",14);
		close(cli);
		printf("Server busy. Connection closed: %s\n",inet_ntoa(addr.sin_addr));
		return;
	}
	//if(setsockopt(cli,SOL_SOCKET,SO_RCVTIMEO,&t,sizeof(t)) == -1) {
	//	write(cli,"E\x11Server is broken.",19);
	//	close(cli);
	//	printf("setsockopt() errored. Connection closed: %s\n",inet_ntoa(addr.sin_addr));
	//	return;
	//}
	Clients[i] = cli;
	PlayableIDs[i] = -1;
	ClientUIDs[i] = -1;
	ClientPing[i] = -1;
	FirstDataSend[i] = 1;
	MessagePointer[i] = GlobalMessagePointer;
	if(pthread_create(&p,NULL,server_worker,(void*)(long int)i) != 0) {
		//pthread creation failed
		write(cli,"E\x0dServer error.",15);
		close(cli);
		printf("pthread_create() failed. Connection closed: %s\n",inet_ntoa(addr.sin_addr));
		Clients[i] = -1;
	}
	netlog(i,"Connected: %s\n",inet_ntoa(addr.sin_addr));
}

void *server_worker(void* data) {
	//Do not attempt to send some data directly to client in this thread.
	//write() is not thread safe.
	int id = (long int)data;
	//Command Receiver Handler
	write(Clients[id],"O",1); //Send ACK
	PutMessage(id,"Please input your word.");
	//Send all logged users
	for(int i = 0;i < MAX_CONNECTIONS;i++) {
		if(Clients[i] != -1 && PlayableIDs[i] != -1 && ClientUIDs[i] != -1) {
			PutMessage(id,"\x02%d\1%s",PlayableIDs[i],getusername(ClientUIDs[i]));
		}
	}
	int c;
	while(Clients[id] != -1 && CloseServer == 0) {
		//Server loop. Decode commands the send info.
		c = readuint8(Clients[id]);
		//Do server work
		/*
		Server commands are...
		Q ... Q Skill Signal (Nxxyy: N: 'Q' or 'W' or 'E' or 'R' or 'L', xx: MapX, yy: MapY)
		W ... W Skill Signal Same as above
		E ... E Skill Signal Same as above
		R ... R Skill Signal Same as above
		L ... Map Click Signal Same as above
		M ... Message signal (Mi[c]: M: 'M', i: length([c]), [c]: data[])
		I ... Get Information (I: 'I')
		*/
		if(c == 'Q' || c == 'W' || c == 'E' || c == 'R' || c == 'L') {
			uint8_t b[4];
			int t = readdata(Clients[id],b,4);
			uint16_t x = b[0] * 0x100 + b[1];
			uint16_t y = b[2] * 0x100 + b[3];
			if(t == -1) { break; } //when timed out
			if(c == 'Q') {
				QHandler(id,x,y);
			} else if(c == 'W') {
				WHandler(id,x,y);
			} else if(c == 'E') {
				EHandler(id,x,y);
			} else if(c == 'R') {
				RHandler(id,x,y);
			} else if(c == 'L') {
				MapClickHandler(id,x,y);
			}
		} else if(c == 'M') {
			int l = readuint8(Clients[id]);
			if(l == -1) {break;} //when timed out
			char buffer[l + 1];
			l = readdata(Clients[id],buffer,l);
			if(l == -1) {break;} //when timed out
			buffer[l] = '\0';
			UserDataHandler(id,buffer);
		} else if(c == 'I') {
			clock_t s = clock();
			if(WritingInfoHandler(id) == -1) {break;} //write error
			ClientPing[id] = clock() - s;
		} else if(c == -1) {
			break;
		} else {
			netlog(id,"Bad command (%02x)!\n",c);
			break;
		}
	}
	if(errno == EAGAIN || errno == EWOULDBLOCK) {
		netlog(id,"Disconnected because of read() timed out!\n");
	} else if(errno != 0) {
		netlog(id,"Disconnected because of read() error: %s\n",strerror(errno));
	} else {
		netlog(id,"Client disconnected.\n");
	}
	if(ClientUIDs[id] != -1) {
		RemoveCharacter(PlayableIDs[id]);
		PutMessage(-1,"\x03%s",getusername(ClientUIDs[id]));
	}
	close(Clients[id]);  //Do not close id itself, but Clients[id]. If you close id and id was 1, you killed printf().
	Clients[id] = -1;
	return NULL;
}

int WritingInfoHandler(int id) {
	int i;
	//Determine which element should be sent
	int ulen = 0;
	int uids[MAX_CHARAS];
	int dlen = 0;
	int dids[MAX_CHARAS];
	for(i = 0;i < MAX_CHARAS; i++) {
		if(CHARA[i].updateflag[id] == 1 || FirstDataSend[id] == 1) {
			if(isCharaActive(i)) {
				uids[ulen] = i;
				ulen++;
			} else {
				dids[dlen] = i;
				dlen++;
			}
		}
	}
	FirstDataSend[id] = 0;
	int ml = 0;
	char *buf;
	//Readonly, mutex not required, see util.c
	if(MessagePointer[id] != GlobalMessagePointer) {
		int p = MessagePointer[id];
		if(MessagesSendOnly[p] == -1 || MessagesSendOnly[p] == id) {
			buf = MessageBuffer[p];
			ml = strlen(buf);
			//netlog2(id,"Message will sent, %s, %d\n",buf,ml);
		}
	}
	//Returned information format
	//AADDMqwer[iiaaaaaaaaaa][dd][m]
	//AA (uint16_t): number of [iiaaaaaaaaaa], DD (uint16_t): number of [dd], M (uint8_t): number of [m]
	//q (uint8_t): Q cooldown, w (uint8_t): W cooldown, e (uint8_t): E cooldown, r (uint8_t): R cooldown
	//[iiaaaaaaaaaa] (uint16_t+uint8_t+uint16_t+uint16_t+uint8_t+uint8_t+uint8_t+uint8_t+uint8_t): altered character datas, ii (uint16_t): altered indexes
	//[dd] (uint16_t) : uint16_t deleted character indexes, [m] (uint8_t) : extra datas
	uint8_t databuf[9 + ml + (ulen * 12) + (dlen * 2)];
	putuint16(databuf,0,ulen); //AA
	putuint16(databuf,2,dlen); //DD
	databuf[4] = ml; //M
	//Section1 Cooldowns
	if(isCharaActive(PlayableIDs[id])) {
		character_t p = CHARA[PlayableIDs[id]];
		databuf[5] = p.Qcd; //q
		databuf[6] = p.Wcd; //w
		databuf[7] = p.Ecd; //e
		databuf[8] = p.Rcd; //r
	} else {
		memcpy(&databuf[5],"\0\0\0\0",4); //qwer
	}
	//Section2 Element informations
	int dataoff = 9;
	for(i = 0;i < ulen; i++) {
		character_t e = CHARA[uids[i]];
		putuint16(databuf,dataoff,uids[i]); //[ii
		//aaaaaaaaa]
		databuf[dataoff + 2] = e.imageid;
		putuint16(databuf,dataoff + 3,(uint16_t)e.x); 
		putuint16(databuf,dataoff + 5,(uint16_t)e.y);
		databuf[dataoff + 7] = e.w;
		databuf[dataoff + 8] = e.h;
		databuf[dataoff + 9] = e.rotate;
		databuf[dataoff + 10] = e.hprate;
		databuf[dataoff + 11] = e.statusflag;
		dataoff += 12;
		CHARA[uids[i]].updateflag[id] = 0;
	}
	for(i = 0;i < dlen;i++) {
		putuint16(databuf,dataoff,dids[i]); //[dd]
		CHARA[dids[i]].updateflag[id] = 0;
		dataoff += 2;
	}
	////Section 3: Send Mesage buffer
	memcpy(&databuf[dataoff],buf,ml); //[m]
	//Send all data
	if(write(Clients[id],databuf,sizeof(databuf)) != sizeof(databuf)) {return -1;}
	if(GlobalMessagePointer != MessagePointer[id]) {if(MessagePointer[id] < MAX_MSGBUF - 1) {MessagePointer[id]++;} else {MessagePointer[id] = 0;}} //Mark message was sent
	return 0;
}
