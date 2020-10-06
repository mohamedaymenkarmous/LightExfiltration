# LightExfiltration

<p align="center">
<img src="logo.png" width="40%"/>
</p>


## Introduction
This project was created during an [IoT challenge](https://www.hackfest.tn/iot-challenge/) ([backup description in case the first link is unavailable](https://web.archive.org/web/20201004004507/https://www.hackfest.tn/iot-challenge/)) ran in the [Hackfest](https://www.hackfest.tn/) event with the name "Data Exfiltration Via Smart Plugs".


## Description
This project includes a study and a PoC regarding the possibility of the data exfiltration using the light. The study was performed using:

- A smart plug that have already a lamp configurable with multiple color setting.
- An [emitter](https://github.com/mohamedaymenkarmous/LightExfiltration/tree/main/Emitter) that is supposed to exfiltrate the data using the smart plug's lamp.
- A [receiver](https://github.com/mohamedaymenkarmous/LightExfiltration/tree/main/Receiver) that is supposed to use what the smart plug's lamp have shown to extract the exfiltrated data.

The "[report.pdf](light-exfiltration-report.pdf)" file describes in detail how the emitter and the receiver works.


## Demo
Using the smart plug, the [emitter](https://github.com/mohamedaymenkarmous/LightExfiltration/tree/main/Emitter) and the [receiver](https://github.com/mohamedaymenkarmous/LightExfiltration/tree/main/Receiver), we have shared some demonstration examples showing how the data could be exfiltrated using the light width different colors and different parameters and how the exfiltrated data was retrieved synchronously (real time using the mobile application) and asnychrnously (offline using the Python script):
- [https://drive.google.com/drive/folders/1L5SHnHZmVoqQA2tB9qoQ4I0QdYRwPu4y?usp=sharing](https://drive.google.com/drive/folders/1L5SHnHZmVoqQA2tB9qoQ4I0QdYRwPu4y?usp=sharing)

As a side note, if anyone was reading the report and he was interested in how we performed the variation of the Hue with a fixed value of the Saturation (section D.2), the resources that allow him to reproduce the test are available here:
- [https://drive.google.com/drive/folders/1ZCoBhRzcK1DeaUd4nJt3rMM6ZFUzyFrE?usp=sharing](https://drive.google.com/drive/folders/1ZCoBhRzcK1DeaUd4nJt3rMM6ZFUzyFrE?usp=sharing)

## Authors
- [Mohamed Ali Belhaj Ali (\@v3rlust)](https://github.com/v3rlust)
- [Mohamed Aymen Karmous (\@mohamedaymenkarmous)](https://github.com/mohamedaymenkarmous)

We represented the team "The Emperors" during this project
