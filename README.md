# SET GAME

## OVERVIEW

This project implements a simplified version of the game "Set" in Java, focusing on concurrent programming concepts and unit testing. The game involves finding combinations of three cards that constitute a "legal set" based on their features. The project provides a framework for game logic, player interactions, and graphical user interface, with the main goal being the implementation of game logic.
All Based on: https://en.wikipedia.org/wiki/Set_(card_game) 

## FEATURES
- **Deck of Cards**: Contains 81 cards with various features such as color, number, shape, and shading.
- **AI Players**: Supports AI players with changeable reaction times.
- **1v1 Gameplay**: Designed for one-on-one matches.
- **Concurrency**: Utilizes Java threads and synchronization mechanisms for concurrent gameplay.
- **Changeable Config**: Customize the game experience with configurable parameters such as keys, gridSize, pentaly\point freeze time and more.


## HOW TO PLAY [Default Config]
![image](https://github.com/miniNemiku/Set_Card_Game/assets/155912382/6f84e1a0-2606-44ef-990f-0af85ebc2089)

   
## RUN
1. Clone the repository to your local machine.
2. Ensure you have Maven installed. https://maven.apache.org/download.cgi
3. Navigate to the pom.xml directory in your terminal.
4. Compile the project using Maven: `mvn compile`
5. Run the project: `mvn exec:java`
6. Follow the on-screen instructions to interact with the game. Human players can use the designated keys on the keyboard to place or remove tokens from cards. Non-human players are simulated by threads that produce random key presses.


## FLOW 
![image](https://github.com/miniNemiku/Set_Card_Game/assets/155912382/87e16403-2a18-4976-9814-e4fd3fc64a1c)


## Authors
Matan Elkaim & Adi Shugal
