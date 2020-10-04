# Light Exfiltration Receiver

## Overview
In this project, there was two developed receivers:
- The mobile application
- The Python script

# Idea
As described in the PDF report, the idea was to implement two types of receivers:
- A synchronous receiver that can retrieve the exfiltrated data in real time from the camera and in this project the mobile application was used to do this job.
- An asynchronous receiver that can retireve the exfiltrated data from a recorded video that can be used any time and also whenever the synchronous receiver fails to extract the data because the camera parameters are missconfigured and in this project the python script was used to do this job.

This doesn't mean that the mobile application can only be synchronous and the python script can only be asynchronous. They can be improved to do both jobs (synchronous and asynchronous).
