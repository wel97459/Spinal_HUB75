#include <SDL2/SDL.h>
//#include <SDL2/SDL_ttf.h>
#include <pthread.h>
#include "sim.h"
#include "draw.h"
typedef struct 
{
    int x,y,n;
    unsigned char *data;
} image_data;

float lerp3(float x0, float x1, float x2) {
  // ... existing code ...

  // modified function to perform 3-point interpolation
  return x0 + (x2 - x0) * x1;
  // ... existing code ...
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

SDL_Texture* tex;

void sim_init(int argc, char *argv[]){
    image_data img;
    sim_load_image(&img, "../data/art/sr2a11ca230ebaws3.png");
    tex = createImage(img.data, img.x, img.y);

}

float lerpf(float a, float b, float t) {
  // Calculate the interpolated value.
  // Input: a (start), b (end), t (lerp factor between 0 and 1)
  return a + t * (b - a);
}

void sim_keyevent(int event, int key) {
}

float i=0.0f;
int x_1=0,x_2=32,y_1=0,y_2=64;
Uint32 start_time = 0;
Uint32 delay_time = 0 ;
void sim_run(){
    start_time = SDL_GetTicks();
    if(delay_time < start_time){
        drawSetTarget(displayTexture);

        SDL_SetRenderDrawColor(renderer, 255, 255, 255, 255);
        SDL_RenderClear(renderer);
        drawImageXY(tex, lerpf(-x_1,-x_2,i), lerpf(-y_1,-y_2,i));
        i=i+0.001f;
        drawResetTarget();

        SDL_RenderCopy(renderer, displayTexture, NULL, &ScreenSpace);
        SDL_RenderPresent(renderer);
        delay_time = start_time + 100;
    }
}

void sim_end()
{
}