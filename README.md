# GGP Java codebase for CS227B

A reboot of the original Teapot GGP Player: https://github.com/panchuanbo/teapot-ggp-base

A fun side project involving general game playing. Original code from the Stanford CS227B course and forked from the ggp-org repo.

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
