# Light Exfiltration Receiver (Python script)

## Prerequisite
The receiver script requires Python3 installed

## Installation
```
pip3 install -r requirements.txt
chmod u+x LightExfiltrationReceiver.py
```

## Execution
### Help
```
./LightExfiltrationReceiver.py
```

### Output
```
You have to choose at least one action from --actionstats and --actiondecode
Command:
                python3 VideoDecoder.py -i <video_file> -m <mode> -a action [Arguments,...]

Arguments:
                 -i,--ivideo=<vide_file> : File path of the video that
                     needs to be proceeded to extract the exfiltrated data from it
                 -m,--mode=[manchester|circular]
                         [x] manchester mode is using Manchester
                     encoding algorithm. It will enforce the bits number to 1
                         [x] circular mode is using the HSV color
                     representation. The S is used to detect every bit state and the
                     HS are used to detect the value of the bit
                 --actionstats : Show the statistical variations of
                     the HS coordinates (from the HSV color representation) inside
                     the video for every processed frame. The results obtained from
                     the statistics will help to calculate what are the best values
                     that should be used to decode the data hidden within the video
                     (--frate,--minhue, --minsaturation, --radius, etc). The
                     statistics will be stored in a .png file using the
                     --statsoutput parameter while the script is running for every
                     frame so you can reload the image every time a video frame is
                     processed to have a statistical overview about the HS
                     coordinates
                 --actiondecode : Extract the encoded data that is
                     hidden inside the video
                 --statsoutput=<path-image.png> : The name of the .png
                     file that will contain the statistical variations of the HS
                     coordinates inside the video for every processed frame. The
                     default value is <video_file>.png where <video_file> is the
                     parameter passed to -i/--ivideo contatenated with ".png"
                 --bitsnumber=<int-value> : The number of bits used to
                     decode the data extracted from the video
                         [x] For manchester mode the value of
                     <bitsnumber> is enforced to 1
                         [x] For circular mode the default value of
                     <bitsnumber> is enforced to 1 and it can be customized to any
                     integer value greater or equal to 1. Every extracted color will
                     match with the couple (H,S). The detected state represent
                     <bitnumber> bits at the same time. This mean that during the
                     reconnaissance step, the script spotted 2 exponent
                     (<bitsnumber>+1) values. For example if <bitsnumber> equals to
                     2, there will be 8 couples of (H,S) captured during the
                     reconnaissance (calculated as 4 values of H and every H is
                     known to have 2 values of S since when the same bit is sent
                     twice, the S will always change to the opposite value to notify
                     the recipient that another new bit was sent and when we couple
                     the H and the S we will get 4*2 which is equals to 8). Having 8
                     states, only 4 of them will really describe the modulated data.
                     This mean, after the reconnaissance step when the data is
                     extracted, if a color matches with one of the 8 steps, it'
                     really matching with one the 4 values of the H. And that value
                     will be demodulated to bits. For example of the script found
                     that the extracted color matches with the (H,S) couple that is
                     translated as the number 3, it will be 10 in binary
                     representation. This way we explained how we can extract
                     <bitsnumber> at the same time (in this example <bitsnumber>
                     equals to 2)
                 --frate=<float-value|int-value> : Frame rate in
                     seconds. The default value is 1.0
                 --starttime=<float-value|int-value> : The starting
                     time in seconds that will be processed. The older time will be
                     ignored. This feature is used to skip a video part that will
                     make the reconnaissance wrong if it's processed. The default
                     value is 0.0 which means that the video will be processed from
                     the beginning
                 --startdatatime=<float-value|int-value> : After the
                     reconnaissance step, the data will be decoded. If a specific
                     time range that starts from <datatime> should be processed
                     after the reconnaissance skipping by jumping to that time, this
                     feature can allow it to do that. The default value is 0.0 which
                     means that the data will be extracted directly after the
                     reconnaissance step without jumping to any specific time
                 --endtime=<float-value|int-value> : The ending time
                     in seconds that will be processed. The newest time will be
                     ignored. This feature is used to stop processing the video if
                     the <endtime> is reached? Thus, even if the video processing is
                     still in the reconnaissance step, the processing will stop. The
                     default value is -1 which means that the video will be
                     processed until the end of the video. Any other value will
                     enable this feature
                 --minsaturation=<float-value|int-value> : The value
                     limit of variation that if it's stated in the saturation value,
                     that will means a new bit was found. This is applied to the
                     reconnaissance and the data extraction steps. This is also
                     applied to all the modes
                 --minhue=<float-value|int-value> : The value limit of
                     variation that if it's stated in the Hue value, that will means
                     a new bit was found. This is applied to the reconnaissance and
                     the data extraction steps. This is also applied to all the
                     modes
                 --radius=<float-value|int-value> : The radius value
                     of the circle that will be considered when calculating the mean
                     value of all the RGB color for all the pixels inside that
                     circle. Calculating the mean value of the pixels inside tthe
                     circle is used to have a precise prediction how the RGB value
                     changed between all the processed frames. And using a circle
                     will limit the video area that will be processed. The center of
                     the circle is located in the middle of the frame rectangle. The
                     default value is 0.0 which mean that all the frame rectangle
                     will be processed to calculate the RGB mean. To enable this
                     feature, the value of <radius> should be greater than 0.0
                 --showcolors : Show the extracted colors
                 --saveframes : Save every processed frame into a
                     .jpeg image. The --framesdir value should be mentioned
                 --framesdir=<directory-path> : The directory in which
                     the saved frames should be saved. The --saveframes should be
                     used
                 -h,--help : Show this help details
```
