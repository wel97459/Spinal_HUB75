#include <stdio.h>
#include <stdlib.h>
#include <SDL.h>
#include "draw.h"
#include "sim.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

const SDL_Rect ScreenSpace = {(WINDOW_WIDTH/2)-(SCREEN_FINALE_WIDTH/2), (WINDOW_HEIGHT/2)-(SCREEN_FINALE_HEIGHT/2), SCREEN_FINALE_WIDTH, SCREEN_FINALE_HEIGHT};

void setClip(int x, int y, int w, int h)
{
    SDL_Rect clip;
    clip.x = x;
    clip.y = y;
    clip.w = w;
    clip.h = h;
    SDL_RenderSetClipRect(renderer, &clip);
}

SDL_Texture* drawNewTexture(){
    SDL_Texture* tex;
    // Create a new texture with the same properties as the one we are duplicating
    tex = SDL_CreateTexture(renderer, SDL_PIXELFORMAT_RGBA8888, SDL_TEXTUREACCESS_TARGET, SCREEN_WIDTH, SCREEN_HEIGHT);
    SDL_SetTextureBlendMode(tex, SDL_BLENDMODE_BLEND);
    return tex;
}

void drawSetTarget(SDL_Texture* target){
    SDL_SetRenderTarget(renderer, target);
}

void drawResetTarget(){
    SDL_SetRenderTarget(renderer, renderTarget);
}

void drawImage(SDL_Texture* tex, int x, int y, int w, int h){
    SDL_Rect dest;
    dest.x = x;
    dest.y = y;
    dest.w = w;
    dest.h = h;
    SDL_RenderCopy(renderer, tex, NULL, &dest);
}

void drawImageXY(SDL_Texture* tex, int x, int y){
    SDL_Rect dest;
    dest.x = x;
    dest.y = y;
    SDL_QueryTexture(tex, NULL, NULL, &dest.w, &dest.h);
    SDL_RenderCopy(renderer, tex, NULL, &dest);
}
