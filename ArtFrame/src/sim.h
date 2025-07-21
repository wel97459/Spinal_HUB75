#ifndef SIM_CLASS_H
#define SIM_CLASS_H


#define SCREEN_SIZE 8
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define SCREEN_WIDTH_H SCREEN_WIDTH/2
#define SCREEN_HEIGHT_H SCREEN_HEIGHT/2
#define SCREEN_FINALE_WIDTH (SCREEN_WIDTH * SCREEN_SIZE)
#define SCREEN_FINALE_HEIGHT (SCREEN_HEIGHT * SCREEN_SIZE)

#define WINDOW_WIDTH (SCREEN_WIDTH * SCREEN_SIZE)
#define WINDOW_HEIGHT (SCREEN_HEIGHT * SCREEN_SIZE)


extern const SDL_Rect ScreenSpace;


    //Bit functions
    #define BIT_SET(X, Y) 				*(X) |= (1<<Y)
    #define BIT_CLEAR(X, Y) 			*(X) &= ~(1<<Y)
    #define BIT_CHECK(X, Y)				(*(X) & (1<<Y))
    #define BIT_TOGGLE(X, Y)			*(X) ^= (1<<Y)
    #define SHIFT(X)                    (1<<X)
    #define SHIFT_MSB(X)                (0x8000>>X)

    extern SDL_Renderer *renderer;
    extern SDL_Window *window;
// extren TTF_Font *font;
    extern SDL_Texture *displayTexture;

    void screenshot(const char filename[]);
    void draw();
    void sim_init(int argc, char *argv[]);
    void sim_keyevent(int event, int key);
    void sim_run();
    void sim_end();
#endif