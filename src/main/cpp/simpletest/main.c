/**
 * @file main.c
 * @author yangye
 * @brief Just a test
 * @version 0.1
 * @date 2022-03-25
 * 
 * @copyright Copyright (c) 2022
 * 
 */

#define SIZE 2

int main()
{
  int array[SIZE];
  int sum = 0;
  for (int i = 0; i < SIZE; i++) {
    array[i] = i;
    sum += array[i];
  }
  while (1);
  return 0;
}