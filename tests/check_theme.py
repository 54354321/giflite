from pathlib import Path
import xml.etree.ElementTree as ET

base=Path(__file__).resolve().parents[1]/'app/src/main/res'
def luminance(h):
    channels=[int(h[i:i+2],16)/255 for i in (1,3,5)]
    channels=[c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4 for c in channels]
    return sum(a*b for a,b in zip(channels,(.2126,.7152,.0722)))
def contrast(a,b):
    a,b=sorted((luminance(a),luminance(b)))
    return (b+.05)/(a+.05)
for theme in ('values','values-night'):
    palette={e.attrib['name']:e.text for e in ET.parse(base/theme/'colors.xml').getroot()}
    for foreground,background in [('ink','surface'),('muted','surface'),('ink','page'),('muted','page'),('on_accent','accent'),('accent','soft')]:
        value=contrast(palette[foreground],palette[background])
        assert value>=4.5,(theme,foreground,background,value)
        print(theme,foreground,background,round(value,2),'PASS')
ns='{http://schemas.android.com/apk/res/android}'
for theme in ('values','values-night'):
    style=ET.parse(base/theme/'styles.xml').getroot()[0]
    assert next(e.text for e in style if e.attrib['name']=='android:forceDarkAllowed')=='false'
icon=ET.parse(base/'mipmap-anydpi-v33/ic_launcher.xml').getroot()
assert icon.find('monochrome').attrib[ns+'drawable']=='@drawable/ic_monochrome'
print('Theme contrast, media inversion exclusion, and monochrome icon resources: PASS')
