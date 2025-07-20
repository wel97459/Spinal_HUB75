cmake_minimum_required(VERSION 3.10)

SET(CMAKE_CXX_STANDARD 11)

set(CMAKE_BUILD_TYPE Debug)
#SET(CMAKE_BUILD_TYPE Relese)

set(CMAKE_BUILD_TYPE RelWithDebInfo)

project(artframe)

find_package(SDL2 REQUIRED)

include_directories(${SDL2_INCLUDE_DIRS})


add_executable(${CMAKE_PROJECT_NAME}
  ./src/mpsse.c
  ./src/sim.c
  ./src/draw.c
  ./src/main.c
)

target_link_libraries(${CMAKE_PROJECT_NAME} m ftdi ${SDL2_LIBRARIES})