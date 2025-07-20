#ifndef DRAW_H
#define DRAW_H
    #include "stb_image_write.h"
    #include "stb_image.h"

    extern SDL_Texture* renderTarget;

    void setClip(int x, int y, int w, int h);
    SDL_Texture* drawNewTexture();
    void drawSetTarget(SDL_Texture* target);
    void drawResetTarget();
    void drawImage(SDL_Texture* tex, int x, int y, int w, int h);
    void drawImageXY(SDL_Texture* tex, int x, int y);
#endif