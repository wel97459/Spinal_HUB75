#include <SDL2/SDL.h>
//#include <SDL2/SDL_ttf.h>
#include <pthread.h>
#include <stdbool.h>
#include <unistd.h> 
#include "sim.h"
#include "draw.h"
#include "mpsse.h"
struct mpsse_context *mpsse = NULL;
const char *devstr = NULL;

typedef struct 
{
    int x,y,n;
    unsigned char *data;
} image_data;

// ---------------------------------------------------------
// Hardware specific CS, CReset, CDone functions
// ---------------------------------------------------------

static void set_cs_creset(bool cs_b, bool creset_b, bool cs_fpga)
{
	uint8_t gpio = 0;
	uint8_t direction = ADBUS0 | ADBUS1;

	if (!cs_b) {
		direction |= ADBUS4;
	}

	if (!creset_b) {
		direction |= ADBUS7;
	}

	if (!cs_fpga) {
		direction |= ADBUS5;
	}

	mpsse_set_gpio(gpio, direction);
}

static bool get_cdone(void)
{
	// ADBUS6 (GPIOL2)
	return (mpsse_readb_low() & 0x40) != 0;
}

// ---------------------------------------------------------
// FLASH function implementations
// ---------------------------------------------------------

// the FPGA reset is released so also FLASH chip select should be deasserted
static void release_all()
{
	set_cs_creset(true, true, true);
}

// FLASH chip select assert
// should only happen while FPGA reset is asserted
static void flash_chip_select()
{
	set_cs_creset(false, false, true);
}

// FLASH chip select deassert
static void flash_chip_deselect()
{
	set_cs_creset(true, false, true);
}

// SRAM reset is the same as flash_chip_select()
// For ease of code reading we use this function instead
static void sram_reset()
{
	// Asserting chip select and reset lines
	set_cs_creset(false, false, true);
}

// SRAM chip select assert
// When accessing FPGA SRAM the reset should be released
static void sram_chip_select()
{
	set_cs_creset(false, true, true);
}

static void chip_select_fpga()
{
	set_cs_creset(true, true, false);
}

void drawBuffer(unsigned char *rgb565, const size_t len)
{
    chip_select_fpga();
	usleep(10000);
	uint8_t command[3] = { 0xA0, 0x00, 0x00 };
	mpsse_send_spi(command, 3);
	mpsse_send_spi(rgb565, len);
    release_all();
}

void setBrightness(const char level)
{
	chip_select_fpga();
	usleep(100000);
	uint8_t command[1] = { 0x10 | level & 0x03};
	mpsse_send_spi(command, 1);
    release_all();
}

void flipBuffer()
{
	chip_select_fpga();
	usleep(10000);
	uint8_t command[1] = { 0x20};
	mpsse_send_spi(command, 1);
    release_all();
}

size_t RGB888toRGB565(unsigned char *rgb888, unsigned char *rgb565, const size_t len, const size_t n)
{
    size_t j=0;
    for(size_t i=0; i < len*n; i+=n)
    {
        uint16_t r=rgb888[i+2], g=rgb888[i+1], b=rgb888[i];
        uint16_t rgb = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
        rgb565[j++] = rgb & 0xff;
        rgb565[j++] = rgb >> 8;
    }
	return len*sizeof(uint16_t);
}

int sim_load_image(image_data *img, const char *filename)
{
    img->data = stbi_load(filename, &img->x, &img->y, &img->n, 0);
    return img->data != NULL;
}

SDL_Texture* createImage(unsigned char* data, int w, int h){
    SDL_Texture* tex;
    SDL_Surface* surface = SDL_CreateRGBSurfaceWithFormat(0, w, h, 24, SDL_PIXELFORMAT_RGB888);
    if (!surface) {
        printf("Failed to create surface: %s\n", SDL_GetError());
        return NULL;
        // Cleanup code
    }

    // Copy the pixel data into the surface
    memcpy(surface->pixels, data, surface->pitch * surface->h);


    tex = SDL_CreateTextureFromSurface(renderer, surface);

    // Cleanup the individual surface
    SDL_FreeSurface(surface);
    return tex;
}

image_data img;
SDL_Texture* tex;
size_t len;
unsigned char *data565;

float lerpf(float a, float b, float t) {
  // Calculate the interpolated value.
  // Input: a (start), b (end), t (lerp factor between 0 and 1)
  return a + t * (b - a);
}

void sim_keyevent(int event, int key) {
}


void lerpf_p3(SDL_Point* p, SDL_Point *p1, SDL_Point *p2, SDL_Point *p3, const float t)
{
    p->x = lerpf(lerpf(p1->x, p2->x, t), lerpf(p2->x, p3->x, t), t);
    p->y = lerpf(lerpf(p1->y, p2->y, t), lerpf(p2->y, p3->y, t), t);
}

void lerpf_p2(SDL_Point* p, SDL_Point *p1, SDL_Point *p2, const float t)
{
    p->x = lerpf(p1->x, p2->x ,t);
    p->y = lerpf(p1->y, p2->y, t);
}


float get_distance(SDL_Point* p1, SDL_Point *p2) {
  // Calculate the distance using the Pythagorean theorem
  return sqrt(pow(p2->x - p1->x, 2) + pow(p2->y - p1->y, 2));
}

void toScreen()
{
	// Create an empty RGB surface that will be used to create the screenshot bmp file
	SDL_Surface* pScreenShot = SDL_CreateRGBSurfaceWithFormat(0, 128, 64, 32, SDL_PIXELFORMAT_RGB888);
	
	// Read the pixels from the current render target and save them onto the surface
	SDL_RenderReadPixels(renderer, NULL, SDL_PIXELFORMAT_RGB888, pScreenShot->pixels, pScreenShot->pitch);
    len = RGB888toRGB565(pScreenShot->pixels, data565, 128*64, 4);
	// Destroy the screenshot surface
	SDL_FreeSurface(pScreenShot);
}

float i=0.0f, newI = 0.0f, dis=0.0;
int x_1=0,x_2=32,y_1=0,y_2=64;
Uint32 start_time = 0;
Uint32 last_time = 0;
Uint32 delay_time = 0;

SDL_Point p_1 = {0,0};
SDL_Point p_2 = {188,19};
SDL_Point p_3 = {96,203};

void newPoint()
{
    p_1.x = p_2.x;
    p_1.y = p_2.y;
    p_2.x = (rand() % (img.x - SCREEN_WIDTH));
    p_2.y = (rand() % (img.y - SCREEN_HEIGHT));
    
    dis = get_distance(&p_1, &p_2);
    printf("x: %i, y: %i, dis: %f\n", p_2.x, p_2.y, dis);
}

void sim_init(int argc, char *argv[]){
    sim_load_image(&img, "../data/art/sr5z15f9ff4cbdaws3.png");
    tex = createImage(img.data, img.x, img.y);

    data565 = (unsigned char *) malloc((SCREEN_WIDTH*SCREEN_HEIGHT)*sizeof(uint16_t));

    newPoint();
    newPoint();
	//mpsse_init(0, devstr, false);
	//setBrightness(0x2);
}

void sim_run(){
    start_time = SDL_GetTicks();
    if(delay_time < start_time){
        drawSetTarget(displayTexture);

        SDL_SetRenderDrawColor(renderer, 255, 255, 255, 255);
        SDL_RenderClear(renderer);
        SDL_Point p;
        lerpf_p2(&p, &p_1, &p_2, i);

        drawImageXY(tex, -p.x, -p.y);
        newI = (start_time-last_time) / (1000.0 + (dis*100.0));
        if(i+newI>=1){
            i=0;
            newPoint();
            printf("inew: %f\n", newI);
        }else{
            i=i+newI;
        }
        //toScreen();
        drawResetTarget();
        //drawBuffer(data565, len);
		//flipBuffer();
        SDL_RenderCopy(renderer, displayTexture, NULL, &ScreenSpace);
        SDL_RenderPresent(renderer);
        delay_time = start_time + 100;
        last_time = start_time;
    }
}

void sim_end()
{
}