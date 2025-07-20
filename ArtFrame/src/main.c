#include <SDL2/SDL.h>
//#include <SDL2/SDL_ttf.h>
#include "sim.h"
#include "draw.h"
//SDL renderer and single font (leaving global for simplicity)
SDL_Renderer *renderer;
SDL_Window *window;
// TTF_Font *font;
SDL_Texture *renderTarget;
SDL_Texture *displayTexture;


void SetAlpha(void *pixels){
    union p
    {
        void *p;
        char *c;
    };
    union p pix;
    pix.p = pixels;
    for (size_t i = 3; i < (WINDOW_HEIGHT*WINDOW_WIDTH)*4; i+=4)
    {
        pix.c[i] = pix.c[i-3];
        pix.c[i-3] = pix.c[i-1];
        pix.c[i-1] = pix.c[i];
        pix.c[i] = 0xff;
    }
}

void screenshot(const char filename[])
{
	// Create an empty RGB surface that will be used to create the screenshot bmp file
	SDL_Surface* pScreenShot = SDL_CreateRGBSurface(0, WINDOW_WIDTH, WINDOW_HEIGHT, 32, 0x000000FF, 0x0000FF00, 0x00FF0000, 0x00000000);
	
	// Read the pixels from the current render target and save them onto the surface
	SDL_RenderReadPixels(renderer, NULL, SDL_GetWindowPixelFormat(window), pScreenShot->pixels, pScreenShot->pitch);
    
	// Create the bmp screenshot file
	//SDL_SaveBMP(pScreenShot, filename);
    SetAlpha(pScreenShot->pixels);
    
    stbi_write_png(filename, WINDOW_WIDTH, WINDOW_HEIGHT, 4, pScreenShot->pixels, WINDOW_WIDTH*4);
	// Destroy the screenshot surface
	SDL_FreeSurface(pScreenShot);
}

int handleInput()
{
	SDL_Event event;
	//event handling, check for close window, escape key and mouse clicks
	//return -1 when exit requested
	while (SDL_PollEvent(&event)) {
		switch (event.type) {
		case SDL_QUIT:
			return -1;
            break;
		case SDL_KEYDOWN:
			if (event.key.keysym.sym == SDLK_ESCAPE)
				return -1;
            sim_keyevent(SDL_KEYDOWN, event.key.keysym.sym);
            break;
        case SDL_KEYUP:
            sim_keyevent(SDL_KEYUP, event.key.keysym.sym);
            break;
		// case SDL_MOUSEBUTTONDOWN:
		// 	handleMouse(event.button.x, event.button.y);
		// 	break;
		}
    }
    return 0;
}

int initVideo()
{
    //setup SDL with title, 640x480, and load font
	if (SDL_Init(SDL_INIT_VIDEO)) {
		printf("Unable to initialize SDL: %s\n", SDL_GetError());
		return 0;
	}

	window = SDL_CreateWindow("SDL2 - Verilator - SpinalHDL V1.1", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, WINDOW_WIDTH, WINDOW_HEIGHT, 0);
	if (!window) {
		printf("Can't create window: %s\n", SDL_GetError());
		return 0;
	}

    renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED);
    renderTarget = SDL_GetRenderTarget(renderer);
    // /* Initialize the TTF library */
    // if (TTF_Init() < 0) {
    //         fprintf(stderr, "Couldn't initialize TTF: %s\n",SDL_GetError());
    //         SDL_Quit();
    //         return 0;
    // }

    // font = TTF_OpenFont("OpenSans-Regular.ttf", 24);
    // if(!font) {
    //     printf("TTF_OpenFont: %s\n", TTF_GetError());
    //     SDL_Quit();
    //     return 0;
    //     // handle error
    // }

	displayTexture = drawNewTexture();

    draw();
	return 1;
}

void draw()
{
    drawResetTarget();
    SDL_RenderCopy(renderer, displayTexture, NULL, &ScreenSpace);
    SDL_RenderPresent(renderer);
}

int main(int argc, char *argv[])
{
    if(initVideo()==0) return -1;
    sim_init(argc, argv);
    do{
        sim_run();
    } while (handleInput() >= 0); //run until exit requested

done:
    sim_end();

    SDL_DestroyTexture(displayTexture);
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
