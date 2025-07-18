#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include "mpsse.h"

size_t RGB888toRGB565(unsigned char *rgb888, unsigned char *rgb565, const size_t len, const size_t n)
{
    size_t j=0;
    for(size_t i=0; i < len*n; i+=n)
    {
        uint16_t r=rgb888[i], g=rgb888[i+1], b=rgb888[i+2];
        uint16_t rgb = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
        rgb565[j++] = rgb & 0xff;
        rgb565[j++] = rgb >> 8;
    }
	return len*sizeof(uint16_t);
}

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

void doAnimation()
{
	int x,y,n;
	size_t len;
	unsigned char *data;
	unsigned char *data565;
	char file[255];
	size_t i = 1;
	while(true)
	{
		sprintf(file, "../data/output64/frame_%03li.png", i);
		printf("%s\n", file);

	    data = stbi_load(file, &x, &y, &n, 0);
		data565 = (unsigned char *) malloc((x*y)*sizeof(uint16_t));
		len = RGB888toRGB565(data, data565, x*y, n);
		free(data);	
		drawBuffer(data565, len);
		flipBuffer();
		free(data565);
		i++;	
		if(i>94) i=1;
	}
}

int main(int argc, char **argv)
{

    int x,y,n;
	unsigned char *data;
	unsigned char *data565;
    data = stbi_load(argv[1], &x, &y, &n, 0);
    data565 = (unsigned char *) malloc((x*y)*sizeof(uint16_t));
    size_t len = RGB888toRGB565(data, data565, x*y, n);
	free(data);

    struct mpsse_context *mpsse = NULL;
    const char *devstr = NULL;
	mpsse_init(0, devstr, false);

	setBrightness(0x2);
	size_t i = 0;
	while (true)
	{
	
		for (i = 0; i < y-64; i++)
		{
			drawBuffer(&data565[i*(128*2)], (128*64)*2);
			flipBuffer();
		}

		for (i = i; i > 0; i--)
		{
			drawBuffer(&data565[i*(128*2)], (128*64)*2);
			flipBuffer();
		}
		
	}
	done:
	free(data565);
    printf("\nDone.\n");
    mpsse_close();
    return 1;
}
