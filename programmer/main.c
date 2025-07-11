#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

struct termios options;

int set_interface_attribs (int fd, int speed)
{
        struct termios tty;
        memset (&tty, 0, sizeof tty);
        if (tcgetattr (fd, &tty) != 0)
        {
                //error_message ("error %d from tcgetattr", errno);
                return -1;
        }
        
        tcgetattr(fd, &options);

        cfsetispeed(&options, speed);
        cfsetospeed(&options, speed);
		/* 8N1 Mode */
		options.c_cflag &= ~PARENB;   /* Disables the Parity Enable bit(PARENB),So No Parity   */
		options.c_cflag &= ~CSTOPB;   /* CSTOPB = 2 Stop bits,here it is cleared so 1 Stop bit */
		options.c_cflag &= ~CSIZE;	 /* Clears the mask for setting the data size             */
		options.c_cflag |=  CS8;      /* Set the data bits = 8                                 */
		
		options.c_cflag &= ~CRTSCTS;       /* No Hardware flow Control                         */
		options.c_cflag |= CREAD | CLOCAL; /* Enable receiver,Ignore Modem Control lines       */ 
		
		
		options.c_iflag &= ~(IXON | IXOFF | IXANY);          /* Disable XON/XOFF flow control both i/p and o/p */
		options.c_iflag &= ~(ICANON | ECHO | ECHOE | ISIG);  /* Non Cannonical mode                            */

		options.c_oflag &= ~OPOST;/*No Output Processing*/
		
		/* Setting Time outs */
		options.c_cc[VMIN] = 3; /* Read at least 10 characters */
		options.c_cc[VTIME] = 5; /* Wait indefinetly   */

		if((tcsetattr(fd,TCSANOW,&options)) != 0){ /* Set the attributes to the termios structure*/
		    printf("\nERROR ! in Setting attributes");
            return -1;
        }

        fcntl(fd, F_SETFL, FNDELAY);
        return 0;
}

size_t readport(char *buff, size_t len, int fd)
{
    size_t pos=0, l;
    char c[2];
    while (1)
    {
        l = read(fd, &c, 1);

        if(l < 0){
            printf("There was a error.\r\n");
            //tcflush(fd, TCIFLUSH);
            //tcflush(fd, TCIOFLUSH);
            return -1;
        }

        buff[pos++] = c[0];

        if(pos >= len || l == -1 || c[0] == '\n'){
            //tcflush(fd, TCIFLUSH);
            //tcflush(fd, TCIOFLUSH);
            buff[pos] = '\0';
            return pos;
        }
    }
}


void RGB888ToRGB565(unsigned char *RGB888, uint8_t *RGB565, const int n, const size_t len)
{
    int j = 0;
    for (size_t i = 0; i < len; i+=n)
    {
        uint16_t rgb = ((RGB888[i] & 0b11111000) << 8) | ((RGB888[i+1] & 0b11111100) << 3) | (RGB888[i+2]  >> 3);
        RGB565[j++] = rgb & 0xff;
        RGB565[j++] = rgb >> 8;
    }
    
}

void waitOk(int fd)
{
    size_t len;
    char ch[5];
    while (strncmp(ch, "Ok.", 3) != 0)
    {
        len = readport(ch, 5, fd);
    }
    tcflush(fd, TCIOFLUSH);
}

void flipShort(unsigned short *s){
    *s = (*s << 8) | (*s >> 8);
}

int main(int argc, char **argv)
{
    int x,y,n,ok;
    ok = stbi_info(argv[2], &x, &y, &n);
    if(!ok){
        return 1;
    }else{
        printf("Image X: %i, Y: %i, N: %d\n", x , y ,n);
    }
    unsigned char *data = stbi_load(argv[2], &x, &y, &n, 0);
    uint8_t *data565 = (uint8_t*)malloc((x*y) * sizeof(uint16_t));
    RGB888ToRGB565(data, data565, n, (x*y)*n);
    printf("test: 0x%X\n", data565[0]);

    int fd;
    fd = open(argv[1], O_RDWR | O_NOCTTY);
    if (fd < 0) {
        printf("There was a error opening %s, %i\r\n\r\n", argv[1], fd);
        return 0;
    }

    if (set_interface_attribs (fd, B57600) < 0) {
        printf("There was a error setting up the port\r\n\r\n");
        return 0;
    }


    size_t data_len;
    size_t len = (x*y)*sizeof(uint16_t);
    size_t offset=0;
    char ch[256], c[16];

    write (fd, "a0000", 6);
    waitOk(fd);

    printf("File starting at:%lu\n", len);
    int i = 0;
    while(data_len>0){

        data_len = len > 255 ? 255 : len;
        memcpy(ch, data565+offset, data_len);
        len -= data_len;
        offset += data_len;

        if(data_len > 0){
            c[0] = '`';
            c[1] = data_len;
            write (fd, &c, 2);
            
            printf("%c", data_len == 255 ? 'O' : '.');

            write (fd, ch, data_len);
            waitOk(fd);
        }
    }

done:
    printf("Done.\n");
    free(data);
    free(data565);
    close(fd);
    return 1;
}
