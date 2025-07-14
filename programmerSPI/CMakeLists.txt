cmake_minimum_required(VERSION 3.10)
#set(CMAKE_BUILD_TYPE Debug)
#SET(CMAKE_BUILD_TYPE Relese)
SET(CMAKE_CXX_STANDARD 11)
project(Programmer)

add_executable(${CMAKE_PROJECT_NAME}
  ./src/mpsse.c
  ./src/main.c
)

target_link_libraries(${CMAKE_PROJECT_NAME} m ftdi)