# GGP Java codebase for CS227B

A reboot of the original Teapot GGP Player: https://github.com/panchuanbo/teapot-ggp-base

A fun side project involving general game playing. Original code from the Stanford CS227B course and forked from the ggp-org repo.

## How To Run

The recommended way to run this project is to use Eclipse. Once in Eclipse, you should run the `Player` Class.

## Game Playing

* Make sure the client is open
* Create a player on a port
* Go to http://ggp.stanford.edu/gamemaster/index.php
* Go to Games
* Choose a game
* Make sure the IP Address + Port is correct
* Ping & Start!

## Notes

* Currently, it the site is merged with Stanford Arena, so there isn't a way to play Human-v-Computer games (perhaps check other GGP sites)
* check `TODO` to see a list of things that I'm looking to implement

## Original README

* Renames methods to match the notes for the Stanford CS227B course.
* Support two different communication protocols between manager and player.
    * Establishing an http connection (manager connecting in)
    * Player polling manager for messages (player connecting in)
* Support for two different data formats
    * KIF (legacy)
    * HRF
* Includes files that allow for games with incomplete information (though this requires a bit of additional work to set up).

For a tutorial on setup, see the following link: https://docs.google.com/document/d/1wM2AXCZUBmen_M_fN4vk41qdDwkn2bNQ14H8pfz1fRI/edit?usp=sharing
