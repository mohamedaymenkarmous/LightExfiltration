#!/usr/bin/python3

import cv2
import numpy
import colorsys
import matplotlib.pyplot as plt
import time
import binascii
from PIL import Image
import sys, getopt
import os
import textwrap
#from colored import fg, bg, attr
from colr import color

class VideoDecoder:
 ### Local variables
 # Frame cursor (in seconds)
 sec = 0
 # Frame rate (in seconds)
 frameRate = 1.0 #//it will capture image in each 0.5 second
 #count=1
 plot_axis_x=[]
 plot_axis_y_from_H=[]
 plot_axis_y_from_H_normalized=[]
 plot_axis_y_from_S=[]
 #plt.figure(figsize=[50,50])
 #!radius0 = numpy.linspace(0.3, 9, 3000)
 basePixel=[]
 baseHsv=[]
 hsv=[]
 statedHsv=[]
 minHue=1
 minSaturation=1
 advancedMode=None
 timer=0
 previousTime=0
 currentTime=0
 nextResumeTime=0
 colorChanging=False
 reconDone=False
 colorRanges=[]
 newdata=""
 bitsNumber=1
 finalDecodedNewData=""
 start_time=0.0
 time_end=-1
 receive_data_from=0.0
 latest_captured_frame=-1 # this variable is important as a workaroud cause when we set the frame cursor in a specific time and when we read that frame, when the cursor exceed the video duration, the read() function still return the latest image so to solve the infinite loop reading on the last frame, we have to check whether the actual frame time was greater than the previous or not
 vidcap=None
 inputVideo=""
 radiusFilter=0.0
 saveFrames=False
 framesDir=None
 statsOutput=None
 statsCircleIncrementedRadiuses=[]
 statsCircleIncrement=0.1
 fig= None
 axs=None
 showColors=False
 actionDecode=False
 actionStats=False

 def getFrame(self,sec):
    self.vidcap.set(cv2.CAP_PROP_POS_MSEC,self.sec*1000)
    hasFrames,image = self.vidcap.read()
    # the +1000 is for the synchronization. Sometimes we found strange values of self.vidcap.get(cv2.CAP_PROP_POS_MSEC) that can decrease by few milliseconds, get back to the normal values, and so on
    if self.vidcap.get(cv2.CAP_PROP_POS_MSEC)+1000>self.latest_captured_frame:
      self.latest_captured_frame=self.vidcap.get(cv2.CAP_PROP_POS_MSEC)
    else:
      hasFrames=False
      image=None
    return hasFrames,image

 def normalize_h(self,h):
    h_tmp=h
    h1=h_tmp
    h_diff_min=max(h_tmp)-min(h_tmp)
    for i in range(0,len(h)):
      transition_diff=float(360)-h_tmp[i]
      h_tmp=(h_tmp+transition_diff)%360
      if max(h_tmp)-min(h_tmp)<h_diff_min:
        h1=h_tmp
        h_diff_min=max(h_tmp)-min(h_tmp)
    h1=h1-min(h1)
    return h1

 # excludeFirstIndex : if true: skip capturing a color similar to the color registered in the index of colorRanges vector (used only when the record button is clicked to avoid capturing the initial color another time)*/
 def calcMinHSDistance(self,newHsv,excludeFirstIndex):
  minHSDistance=[-1, -1, 1, -1]
  for i in range(0,len(self.colorRanges)):
    # Example 1: (newHsv0=13 & colorRanges=5): colorRanges<newHsv0: 13-5=8
    b1 = abs(newHsv[0] - self.colorRanges[i][0]) < 180
    # Example 2 (newHsv0=359 & colorRanges=3): colorRanges<<newHsv0: 3-(359-360)=3-(-1)=4
    b2 = abs(newHsv[0] - self.colorRanges[i][0]) >= 180 and newHsv[0]>self.colorRanges[i][0]
    # Example 3 (newHsv0=3 & colorRanges=359): colorRanges<<newHsv0: abs((359-360)-3)=abs(-1-3)=4
    b3 = abs(newHsv[0] - self.colorRanges[i][0]) >= 180 and newHsv[0]<self.colorRanges[i][0]
    hDistance=0
    sDistance=0
    if b1:
      hDistance=abs(newHsv[0] - self.colorRanges[i][0]) % 360
    elif b2:
      hDistance=abs(self.colorRanges[i][0] - (newHsv[0] - 360)) % 360
    elif b3:
      hDistance=abs((self.colorRanges[i][0] - 360) - newHsv[0]) % 360
    sDistance=abs(newHsv[1] - self.colorRanges[i][1])
    if hDistance<self.minHue:
      if sDistance<(self.minSaturation/100):
        if i==0 and excludeFirstIndex==True:
          minHSDistance=[0, 0, 0, i]
        else:
          minHSDistance=[0, 0, 1, i]
        break
      elif sDistance>=(self.minSaturation/100):
        minHSDistance=[hDistance, sDistance, 1, i]
    else:
      if minHSDistance[0]<0:
        minHSDistance = [hDistance, sDistance, 1, i]
      else:
        if hDistance<minHSDistance[0]:
          minHSDistance = [hDistance, sDistance, 1, i]
  return minHSDistance

 def calcMinSDistance(self,newHsv,excludeFirstIndex):
  minSDistance=[-1, 1, -1]
  for i in range(0,len(self.colorRanges)):
    sDistance=0
    sDistance=abs(newHsv[1] - self.colorRanges[i][1])
    if sDistance<(self.minSaturation/100) and len(self.colorRanges)>1:
      if i==0 and excludeFirstIndex==True:
        minSDistance = [0, 0, i]
      else:
        minSDistance = [0, 1, i]
      break
    elif sDistance>=(self.minSaturation/100):
      if minSDistance[0]<0:
        minSDistance=[sDistance, 1, i]
      else:
        if sDistance<minSDistance[0]:
          minSDistance=[sDistance, 1, i]
  return minSDistance

 def int2bytes(self,i):
    hex_string = '%x' % i
    n = len(hex_string)
    return binascii.unhexlify(hex_string.zfill(n + (n & 1))).decode()

 def detectBitsByDetectedHSV(self,newPixel,newHsv):
  minHueDistance=[]
  nthBit=0
  bitsNumberPerByte=1
  if self.advancedMode==True:
    minHueDistance=self.calcMinHSDistance(newHsv, False)
    #print(minHueDistance)
    nthBit=minHueDistance[3]/2
    bitsNumberPerByte=8
  else:
    minHueDistance=self.calcMinSDistance(newHsv, False)
    nthBit=minHueDistance[2]
    bitsNumberPerByte=16
  self.newdata = self.newdata + "{0:b}".format(int(nthBit)).zfill(self.bitsNumber)
  print("Encoded : "+self.newdata)
  if len(self.newdata)%bitsNumberPerByte == 0:
    self.finalNewData=self.extractPreDecodedData()
    if self.finalNewData[-8:].find("?")>=0:
      self.finalDecodedNewData=self.finalDecodedNewData+"?"
    else:
      charCode = int(self.finalNewData[-8:], 2)
      self.finalDecodedNewData=self.finalDecodedNewData+self.int2bytes(charCode)
      print("Decoded (Bits) : "+self.finalNewData)
    print("Decoded (ASCII) : "+self.finalDecodedNewData)

 def extractPreDecodedData(self):
  finalNewData=""
  if self.advancedMode==True:
    finalNewData=self.newdata
  else:
    for i in range(0,len(self.newdata),2):
      if self.newdata[i:i+2]=="01":
        finalNewData=finalNewData+"1"
      elif self.newdata[i:i+2]=="10":
        finalNewData=finalNewData+"0"
      else:
        finalNewData=finalNewData+"?"
  return finalNewData

 def setInputVideo(self,path):
    self.inputVideo=path
    self.vidcap = cv2.VideoCapture(self.inputVideo)

 def mandatory(self):
  if self.inputVideo=="":
    print("The input video is mandatory\n")
    showHelp()
    sys.exit(2)
  if self.advancedMode==None:
    print("The mode is mandatory\n")
    showHelp()
    sys.exit(2)
  if self.actionStats==True and self.statsOutput==None and os.path.isdir(self.inputVideo+".png")==True:
    print("You have enable the statistical processing using --actionstats without providing the --statsoutput to write the .png file there. The default value of --statsoutput was used as "+self.inputVideo+".png . Unfortunately, the file name was already used for a directory name. So you have to provide a different file location using --statsoutput")
    sys.exit(2)

 def process(self):
  self.mandatory()
  self.sec = self.sec + self.start_time
  success = self.getFrame(self.sec)
  self.fig, self.axs = plt.subplots(4,figsize=(10,50))
  while success:
    if self.time_end>=0 and self.time_end<=self.sec:
      break
    self.sec = self.sec + self.frameRate
    self.sec = round(self.sec, 2)
    success,image = self.getFrame(self.sec)
    if image is None:
      sucess=False
      print("No additional frames for processing. Done")
      break

    if self.saveFrames==True:
      cv2.imwrite(self.framesDir+"/image-"+str(self.sec)+".jpg", image)
    if self.radiusFilter>0:
      w, h, c=image.shape
      center=(w/2,h/2)
      y,x = numpy.ogrid[-center[0]:w-center[0], -center[1]:h-center[1]]
      mask = x*x + y*y <= self.radiusFilter*self.radiusFilter
      newPixel0=image[mask]
    else:
      newPixel0=image
    mask2 = numpy.any(newPixel0 != [255,255,255], axis=-1)
    newPixel1=newPixel0[mask2]
    mask3 = numpy.any(newPixel1 != [0,0,0], axis=-1)
    newPixel=numpy.mean(newPixel1[mask3],axis=0)
    newHsv=list(colorsys.rgb_to_hsv(newPixel[2]/float(256),newPixel[1]/float(256),newPixel[0]/float(256)))
    newHsv[0]=newHsv[0]*360
    if self.actionDecode==True:
      self.decode(newPixel,newHsv)
    if self.actionStats==True:
      self.stats(newHsv)

 def stats(self,newHsv):
    # The X Axis: contains the seconds
    self.plot_axis_x.append(self.sec)
    # The Y Axis: contains the H value
    self.plot_axis_y_from_H.append(newHsv[0])
    # The Y Axis: contains the H value normalized with the lowest value that starts from 0
    self.plot_axis_y_from_H_normalized=self.normalize_h(self.plot_axis_y_from_H)
    self.plot_axis_y_from_S.append(newHsv[1])

    theta=numpy.radians(newHsv[0])

    # If we could predict the size of statsCircleIncrementedRadiuses (like 3000 for example), we can use the next command, but this is not possible
    #  self.statsCircleIncrementedRadiuses = numpy.linspace(0.3, 9, 3000)
    if len(self.statsCircleIncrementedRadiuses)==0:
      self.statsCircleIncrementedRadiuses=[0.5]
    else:
      self.statsCircleIncrementedRadiuses.append(self.statsCircleIncrementedRadiuses[len(self.statsCircleIncrementedRadiuses)-1]+self.statsCircleIncrement)
    statsRadius=self.statsCircleIncrementedRadiuses[len(self.plot_axis_y_from_H)-1]
    # Drawing a circle for every point as a reference inside the circle
    tt = numpy.linspace(0,2*numpy.pi, 100)
    x0 = statsRadius*numpy.cos(tt)
    y0 = statsRadius*numpy.sin(tt)
    xx= statsRadius*numpy.cos(theta)
    yy= statsRadius*numpy.sin(theta)

    self.axs[0].plot(self.plot_axis_x,self.plot_axis_y_from_H,label = 'H')
    self.axs[0].set_title("Hue's variation over the time (rate="+str(self.frameRate)+" seconds)")
    self.axs[0].set_xlabel("Time (seconds)")
    self.axs[0].set_ylabel("Hue")
    self.axs[1].cla()
    self.axs[1].plot(self.plot_axis_x,self.plot_axis_y_from_H_normalized,label = 'Normalized H')
    self.axs[1].set_title("Normalized Hue's variation over the time (rate="+str(self.frameRate)+" seconds)")
    self.axs[1].set_xlabel("Time (seconds)")
    self.axs[1].set_ylabel("Normalized Hue")
    self.axs[2].plot(self.plot_axis_x,self.plot_axis_y_from_S,label = 'S')
    self.axs[2].set_title("Saturation's variation over the time (rate="+str(self.frameRate)+" seconds)")
    self.axs[2].set_xlabel("Time (seconds)")
    self.axs[2].set_ylabel("Saturation")
    self.axs[3].plot(x0,y0,label='circular representation of H')
    self.axs[3].plot(xx,yy,'o',label = 'circular representation of H', color='black')
    self.axs[3].set_title("Hue's variation over the time in a circular representation (rate="+str(self.frameRate)+" seconds)")
    if self.statsOutput==None:
      self.statsOutput=self.inputVideo+".png"
    try:
      self.fig.savefig(self.statsOutput)
    except ValueError:
      if self.actionStats==True and self.statsOutput==None:
        print("You have enable the statistical processing using --actionstats without providing the --statsoutput to write the .png file there. The default value of --statsoutput was used as "+self.inputVideo+".png . Unfortunately, the file can't be written there. So you have to provide a different file location using --statsoutput")
      elif self.actionStats==True and self.statsOutput!=None:
        print("The provided <statsoutput> directory is not writable")
      else:
        print("An error occured when writing the statistics into the file "+self.statsOutput)
      sys.exit(2)

 def decode(self,newPixel,newHsv):
    #!ploti.append(sec)
    # The Y Axis: contains the H value
#    plot_axis_y_from_H.append(newHsv[0])
    # The Y Axis: contains the H value normalized with the lowest value that starts from 0
#    plot_axis_y_from_H_normalized=self.normalize_h(plot_axis_y_from_H)
#    plot_axis_y_from_S.append(newHsv[1])
    #print(int(newPixel[2]),int(newPixel[1]),int(newPixel[0]))
    if self.showColors:
      print("Time="+str(self.sec)+"\t"+color("(H,S)="+str(newHsv[0])+"  "+str(newHsv[1]), fore=(0, 0, 0), back=(int(newPixel[2]), int(newPixel[1]), int(newPixel[0]))))

#?    theta=numpy.radians(plotx1)
#    theta=numpy.radians(newHsv[0])

    # If we could predict the size of radius0, we can use the next command, but this is not possible
    #radius0 = numpy.linspace(0.3, 9, 3000)
#    if len(self.statsCircleIncrementedRadiuses)==0:
#      self.statsCircleIncrementedRadiuses=[0.5]
#    else:
#      self.statsCircleIncrementedRadiuses.append(self.statsCircleIncrementedRadiuses[len(self.statsCircleIncrementedRadiuses)-1]+self.statsCircleIncrementedRadiuses)
#    statsRadius=radius0[len(plotx1)-1]
    # Drawing a circle for every point as a reference inside the circle
#    tt = numpy.linspace(0,2*numpy.pi, 100)
#    x0 = statsRadius*numpy.cos(tt)
#    y0 = statsRadius*numpy.sin(tt)

    #radius = radisu[:len(plotx1)]
    #radius = theta**2
    #print(theta,radius)
#    xx= radius*numpy.cos(theta)
#    yy= radius*numpy.sin(theta)
#    axs[3].plot(x0,y0,label='circular representation of H')
#    axs[3].plot(xx,yy,'o',label = 'circular representation of H', color='black')
#    axs[1].plot(ploti,ploty1,label = 'y1')
    #rint(plotx11,ploti)
#    axs[2].cla()
#    axs[2].plot(ploti,plotx11,label = 'x1')
#    fig.savefig('/home/emperor/Desktop/plot.png')
    if len(self.basePixel) == 0:
      self.basePixel=newPixel
      self.baseHsv=newHsv
      self.statedHsv=newHsv
    # Example 1: (newHsv0=13 & baseHsv0=5): baseHsv0<newHsv0: 13-5=8
    b1 = abs(newHsv[0] - self.baseHsv[0]) < 180 and abs(newHsv[0] - self.baseHsv[0]) % 360 < self.minHue
    # Example 2 (newHsv0=359 & baseHsv0=3): baseHsv0<<newHsv0: 3-(359-360)=3-(-1)=4
    b2 = abs(newHsv[0] - self.baseHsv[0]) >= 180 and self.baseHsv[0]<newHsv[0] and abs(self.baseHsv[0] - (newHsv[0] - 360)) % 360 < self.minHue
    # Example 3 (newHsv0=3 & baseHsv0=359): baseHsv0>>newHsv0: abs((359-360)-3)=abs(-1-3)=4
    b3 = abs(newHsv[0] - self.baseHsv[0]) >= 180 and self.baseHsv[0]>newHsv[0] and abs((self.baseHsv[0] - 360) - newHsv[0]) % 360 < self.minHue
    # Example 1: (newHsv0=13 & baseHsv0=5): baseHsv0<newHsv0: 13-5=8
    b4 = len(self.statedHsv) >0 and abs(newHsv[0] - self.statedHsv[0]) < 180 and abs(newHsv[0] - self.statedHsv[0]) % 360 >= self.minHue
    # Example 2 (newHsv0=359 & baseHsv0=3): baseHsv0<<newHsv0: 3-(359-360)=3-(-1)=4
    b5 = len(self.statedHsv) >0 and abs(newHsv[0] - self.statedHsv[0]) >= 180 and self.statedHsv[0]<newHsv[0] and abs(self.statedHsv[0] - (newHsv[0] - 360)) % 360 >= self.minHue
    # Example 3 (newHsv0=3 & baseHsv0=359): baseHsv0>>newHsv0: abs((359-360)-3)=abs(-1-3)=4
    b6 = len(self.statedHsv) >0 and abs(newHsv[0] - self.statedHsv[0]) >= 180 and self.statedHsv[0]>newHsv[0] and abs((self.statedHsv[0] - 360) - newHsv[0]) % 360 >= self.minHue
    # Example 1: (newHsv0=13 & baseHsv0=5): baseHsv0<newHsv0: 13-5=8
    b7 = len(self.hsv) >0 and abs(newHsv[0] - self.hsv[0]) < 180 and abs(newHsv[0] - self.hsv[0]) % 360 < self.minHue
    # Example 2 (newHsv0=359 & baseHsv0=3): baseHsv0<<newHsv0: 3-(359-360)=3-(-1)=4
    b8 = len(self.hsv) >0 and abs(newHsv[0] - self.hsv[0]) >= 180 and self.hsv[0]<newHsv[0] and abs(self.hsv[0] - (newHsv[0] - 360)) % 360 < self.minHue
    # Example 3 (newHsv0=3 & baseHsv0=359): baseHsv0>>newHsv0: abs((359-360)-3)=abs(-1-3)=4
    b9 = len(self.hsv) >0 and abs(newHsv[0] - self.hsv[0]) >= 180 and self.hsv[0]>newHsv[0] and abs((self.hsv[0] - 360) - newHsv[0]) % 360 < self.minHue
    if len(self.hsv) > 0 and (b1 or b2 or b3) and abs(newHsv[1] - self.statedHsv[1]) > self.minSaturation / 100 and abs(newHsv[1] - self.baseHsv[1]) < self.minSaturation / 100:
      print("End of extraction")
    #print(self.statedHsv,len(self.hsv) == 0,len(self.hsv) >0 and ((abs(newHsv[1] - self.statedHsv[1]) > self.minSaturation / 100 and self.colorChanging==False)),(b4 or b5 or b6),(self.advancedMode==False and self.timer>0 and self.nextResumeTime<=self.sec))
    #if len(self.hsv) >0:
      #print(self.colorChanging==False,(abs(newHsv[1] - self.statedHsv[1]) > self.minSaturation / 100),b4,b5,b6,(abs(newHsv[1] - self.hsv[1]) < self.minSaturation / 100),b7,b8,b9)
    #if len(self.hsv) == 0 or (abs(newHsv[1] - self.hsv[1]) > self.minSaturation / 100 or b4 or b5 or b6) or (self.advancedMode==False and self.timer>0 and self.nextResumeTime<=self.sec):
    if len(self.hsv) == 0 or ((abs(newHsv[1] - self.statedHsv[1]) > self.minSaturation / 100 or b4 or b5 or b6) and self.colorChanging==False) or (self.advancedMode==False and self.timer>0 and self.nextResumeTime<=self.sec):
      #!print("ok")
      if len(self.hsv) >0 and self.colorChanging==False:
        print("Color changed")
        self.colorChanging=True
      if self.advancedMode==False and self.timer>0 and self.nextResumeTime<=self.sec:
        self.currentTime=self.sec
        self.nextResumeTime= float(self.currentTime+(self.timer*1.25))
      if self.advancedMode==False and len(self.hsv) >0 and (abs(newHsv[1] - self.statedHsv[1]) > self.minSaturation / 100) and self.reconDone==True:
        self.currentTime=self.sec
        self.nextResumeTime= float(self.currentTime+(self.timer*1.25))
        self.previousTime=self.currentTime
    # Stable color means the difference between the previous and the current colors is less than the minSaturation
    elif self.colorChanging==True and (abs(newHsv[1] - self.hsv[1]) < self.minSaturation / 100 and (b7 or b8 or b9)):
        self.statedHsv=newHsv
        if self.reconDone == False:
          if len(self.colorRanges)==0:
            print("Colors reconnaissance")
            self.colorRanges.append(newHsv)
            self.previousTime=self.sec
          elif len(self.colorRanges) > 0:
            minHueDistance=[]
            reconStatus=0
            minDistanceValid=False
            if self.advancedMode==True:
              #ciruclar colors (HS)
              minHSDistance=self.calcMinHSDistance(newHsv, True)
              minHValue=minHSDistance[0]
              minSValue=minHSDistance[1]
              reconStatus=minHSDistance[2]
              if minHValue<self.minHue and minSValue<(self.minSaturation/100):
                minDistanceValid=True
            else:
              #Manchester coding (S)
              minHDistance=self.calcMinSDistance(newHsv, True)
              minSValue=minHDistance[0]
              reconStatus=minHDistance[1]
              if minSValue<(self.minSaturation/100):
                minDistanceValid=True
            if reconStatus == 0:
                self.reconDone = True
                print("Receiving data")
                if self.receive_data_from>0 and self.sec<self.receive_data_from:
                  self.sec = self.receive_data_from
                self.currentTime=self.sec
                if self.advancedMode==False:
                  self.timer=(self.currentTime-self.previousTime)/4
                else:
                  self.timer=(self.currentTime-self.previousTime)/2
                print("Detected timer: "+str(self.timer)+" sec")
                print("Detected colors: ",self.colorRanges)
                self.nextResumeTime= float(self.currentTime+(self.timer*1.25))
                self.previousTime=self.currentTime
            elif minDistanceValid==False:
                print("newHsv added")
                self.colorRanges.append(newHsv)
              #!print("colorRanges",colorRanges)
            # In reconnaissance, the min distance should not be close to one of the recorded colorRanges values otherwise it will be be added to colorRanges
            else:
              print("Duplicate color that will not be added")
        else:
          self.detectBitsByDetectedHSV(newPixel, newHsv)
        self.colorChanging=False
    #timer = sec + 200
    self.hsv=newHsv
    #pixel = newPixel

def printWrap(text):
  print(textwrap.fill(text, width=70, initial_indent='', subsequent_indent='\t\t     '))

#  print('\n'.join(line.strip() for line in re.findall(r'.{1,80}(?:\s+|$)', text)))

def showHelp():
  print ('Command:')
  print ('\t\tpython3 VideoDecoder.py -i <video_file> -m <mode> -a action [Arguments,...]')
  print ('')
  print ('Arguments:')
  printWrap ('\t\t -i,--ivideo=<vide_file> : File path of the video that needs to be proceeded to extract the exfiltrated data from it')
  printWrap ('\t\t -m,--mode=[manchester|circular]')
  printWrap ('\t\t\t [x] manchester mode is using Manchester encoding algorithm. It will enforce the bits number to 1')
  printWrap ('\t\t\t [x] circular mode is using the HSV color representation. The S is used to detect every bit state and the HS are used to detect the value of the bit')
  printWrap ('\t\t --actionstats : Show the statistical variations of the HS coordinates (from the HSV color representation) inside the video for every processed frame. The results obtained from the statistics will help to calculate what are the best values that should be used to decode the data hidden within the video (--frate,--minhue, --minsaturation, --radius, etc). The statistics will be stored in a .png file using the --statsoutput parameter while the script is running for every frame so you can reload the image every time a video frame is processed to have a statistical overview about the HS coordinates')
  printWrap ('\t\t --actiondecode : Extract the encoded data that is hidden inside the video')
  printWrap ('\t\t --statsoutput=<path-image.png> : The name of the .png file that will contain the statistical variations of the HS coordinates inside the video for every processed frame. The default value is <video_file>.png where <video_file> is the parameter passed to -i/--ivideo contatenated with ".png"')
  printWrap ('\t\t --bitsnumber=<int-value> : The number of bits used to decode the data extracted from the video')
  printWrap ('\t\t\t [x] For manchester mode the value of <bitsnumber> is enforced to 1')
  printWrap ('\t\t\t [x] For circular mode the default value of <bitsnumber> is enforced to 1 and it can be customized to any integer value greater or equal to 1. Every extracted color will match with the couple (H,S). The detected state represent <bitnumber> bits at the same time. This mean that during the reconnaissance step, the script spotted 2 exponent (<bitsnumber>+1) values. For example if <bitsnumber> equals to 2, there will be 8 couples of (H,S) captured during the reconnaissance (calculated as 4 values of H and every H is known to have 2 values of S since when the same bit is sent twice, the S will always change to the opposite value to notify the recipient that another new bit was sent and when we couple the H and the S we will get 4*2 which is equals to 8). Having 8 states, only 4 of them will really describe the modulated data. This mean, after the reconnaissance step when the data is extracted, if a color matches with one of the 8 steps, it\' really matching with one the 4 values of the H. And that value will be demodulated to bits. For example of the script found that the extracted color matches with the (H,S) couple that is translated as the number 3, it will be 10 in binary representation. This way we explained how we can extract <bitsnumber> at the same time (in this example <bitsnumber> equals to 2)')
  printWrap ('\t\t --frate=<float-value|int-value> : Frame rate in seconds. The default value is 1.0')
  printWrap ('\t\t --starttime=<float-value|int-value> : The starting time in seconds that will be processed. The older time will be ignored. This feature is used to skip a video part that will make the reconnaissance wrong if it\'s processed. The default value is 0.0 which means that the video will be processed from the beginning')
  printWrap ('\t\t --startdatatime=<float-value|int-value> : After the reconnaissance step, the data will be decoded. If a specific time range that starts from <datatime> should be processed after the reconnaissance skipping by jumping to that time, this feature can allow it to do that. The default value is 0.0 which means that the data will be extracted directly after the reconnaissance step without jumping to any specific time')
  printWrap ('\t\t --endtime=<float-value|int-value> : The ending time in seconds that will be processed. The newest time will be ignored. This feature is used to stop processing the video if the <endtime> is reached? Thus, even if the video processing is still in the reconnaissance step, the processing will stop. The default value is -1 which means that the video will be processed until the end of the video. Any other value will enable this feature')
  printWrap ('\t\t --minsaturation=<float-value|int-value> : The value limit of variation that if it\'s stated in the saturation value, that will means a new bit was found. This is applied to the reconnaissance and the data extraction steps. This is also applied to all the modes')
  printWrap ('\t\t --minhue=<float-value|int-value> : The value limit of variation that if it\'s stated in the Hue value, that will means a new bit was found. This is applied to the reconnaissance and the data extraction steps. This is also applied to all the modes')
  printWrap ('\t\t --radius=<float-value|int-value> : The radius value of the circle that will be considered when calculating the mean value of all the RGB color for all the pixels inside that circle. Calculating the mean value of the pixels inside tthe circle is used to have a precise prediction how the RGB value changed between all the processed frames. And using a circle will limit the video area that will be processed. The center of the circle is located in the middle of the frame rectangle. The default value is 0.0 which mean that all the frame rectangle will be processed to calculate the RGB mean. To enable this feature, the value of <radius> should be greater than 0.0')
  printWrap ('\t\t --showcolors : Show the extracted colors')
  printWrap ('\t\t --saveframes : Save every processed frame into a .jpeg image. The --framesdir value should be mentioned')
  printWrap ('\t\t --framesdir=<directory-path> : The directory in which the saved frames should be saved. The --saveframes should be used')
  print ('\t\t -h,--help : Show this help details')

def main(argv):
  videoDecoder=VideoDecoder()
  try:
    opts, args = getopt.getopt(argv,"hi:m:",["help","ivideo=","mode=","bitsnumber=","frate=","starttime=","startdatatime=","endtime=","minhue=","minsaturation=","radius=","showcolors","saveframes","framesdir=","actiondecode","actionstats","statsoutput="])
    for opt, arg in opts:
      if opt == '-h':
         showHelp()
         sys.exit()
      if opt in ("-i", "--ivideo"):
         if os.path.isfile(arg):
           videoDecoder.setInputVideo(arg)
         else:
           print("File does not exist")
           sys.exit(2)
  except getopt.GetoptError:
    print("One of the provided parameters might not be correct")
    showHelp()
    sys.exit(2)
  try:
    for opt, arg in opts:
      if opt in ("-m","--mode"):
        if arg=='manchester':
          videoDecoder.advancedMode=False
        elif arg=='circular':
          videoDecoder.advancedMode=True
        else:
          print("Invalid mode\nPossible values: [manchester|circular]")
  except getopt.GetoptError:
    print("One of the provided parameters might not be correct")
    showHelp()
    sys.exit(2)
  try:
    for opt, arg in opts:
      if opt == "--actionstats":
        videoDecoder.actionStats=True
  except getopt.GetoptError:
    print("Something went wrong with the <actionstats> parameter")
    sys.exit(2)
  try:
    for opt, arg in opts:
      if opt == "--actiondecode":
        videoDecoder.actionDecode=True
  except getopt.GetoptError:
    print("Something went wrong with the <actiondecode> parameter")
    sys.exit(2)
  if videoDecoder.actionStats==False and videoDecoder.actionDecode==False:
    print("You have to choose at least one action from --actionstats and --actiondecode")
    showHelp()
    sys.exit(2)
  try:
    for opt, arg in opts:
      if opt == "--frate":
        try:
          videoDecoder.frameRate=float(arg)
        except ValueError:
          print("Invalid frame rate value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--starttime":
        try:
          videoDecoder.start_time=float(arg)
        except ValueError:
          print("Invalid start time value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--startdatatime":
        try:
          videoDecoder.receive_data_from=float(arg)
        except ValueError:
          print("Invalid end data time value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--endtime":
        try:
          videoDecoder.time_end=float(arg)
        except ValueError:
          print("Invalid end time value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--minsaturation":
        try:
          videoDecoder.minSaturation=float(arg)
        except ValueError:
          print("Invalid min saturation value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--minhue":
        try:
          videoDecoder.minHue=float(arg)
        except ValueError:
          print("Invalid min hue value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--radius":
        try:
          videoDecoder.radiusFilter=float(arg)
        except ValueError:
          print("Invalid radius value\nIt should be float or integer")
          sys.exit(2)
      if opt == "--bitsnumber":
        try:
          videoDecoder.bitsNumber=int(arg)
          if videoDecoder.bitsNumber<=0:
            print("Invalid bits number value\nIt should be greater than 0")
            sys.exit(2)
        except ValueError:
          print("Invalid bits number value\nIt should be integer")
          sys.exit(2)
      if opt == "--saveframes":
        try:
          videoDecoder.saveFrames=True
        except ValueError:
          print("Something went wrong with the <saveframes> parameter")
          sys.exit(2)
      if opt == "--showcolors":
        try:
          videoDecoder.showColors=True
        except ValueError:
          print("Something went wrong with the <showcolors> parameter")
          sys.exit(2)
  except getopt.GetoptError:
    print("One of the provided parameters might not be correct")
    showHelp()
    sys.exit(2)
  try:
    for opt, arg in opts:
      if opt == "--framesdir":
        if videoDecoder.saveFrames==False:
          print("You have provided the <framesdir> path. To enable the frames image writing, you have to use the --saveframes option")
          sys.exit(2)
        if os.path.isdir(arg)==False:
          print("The provided <framesdir> is not a directory")
          sys.exit(2)
        if os.access(arg, os.W_OK)==False:
          print("The provided <framesdir> directory is not writable")
          sys.exit(2)
        videoDecoder.framesDir=arg
  except getopt.GetoptError:
    print("One of the provided parameters might not be correct")
    showHelp()
    sys.exit(2)
  if videoDecoder.saveFrames==True and videoDecoder.framesDir==None:
    print("You have provided the --saveframes parameter without providing the directory in which the frame images will be saved using --framesdir")
    sys.exit(2)

  try:
    for opt, arg in opts:
      if opt == "--statsoutput":
        if videoDecoder.actionStats==False:
          print("You have provided the <statsoutput> image location that will be saved. To enable the statistical processing in order to save the output in the provided image location, you have to use the --actionstats option")
          sys.exit(2)
        if os.path.isdir(arg)==True:
          print("The provided <statsoutput> is a directory. It should be a png image (existant or inexistant) instead of an existant directory")
          sys.exit(2)
        videoDecoder.statsOutput=arg
  except getopt.GetoptError:
    print("One of the provided parameters might not be correct")
    showHelp()
    sys.exit(2)

  videoDecoder.process()



if __name__ == "__main__":
   main(sys.argv[1:])
