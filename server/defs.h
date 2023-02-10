#define MAX_CONNECTIONS 20
#define MAX_CHARAS 1000
#define CLIENT_WIDTH 800
#define CLIENT_HEIGHT 600
#define HAS_HP_BAR 0x80
#define CHARA_INVERT 0x40
#define CHARA_ACTIVE 0x8
#define MAX_MSGBUF (MAX_CONNECTIONS * 3)
#define MAP_LIMIT_X 10000
#define MAP_LIMIT_Y 10000

typedef struct {
	uint8_t imageid,w,h,hprate,statusflag,localflag,rotate,Qcd,Wcd,Ecd,Rcd;
	double x,y,sx,sy,tx,ty;
	uint8_t updateflag[MAX_CONNECTIONS];
} character_t;

