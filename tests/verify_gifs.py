from PIL import Image
import json
report=[]
for colors in (64,128,256):
    im=Image.open(f'work/test-{colors}.gif')
    assert im.size==(193,127) and im.n_frames==6
    assert im.info['loop']==0
    total=0
    for n in range(6):
        im.seek(n)
        total+=im.info['duration']
        rgb=im.convert('RGB')
        rgb.load()
        if n==0: assert rgb.getpixel((100,100))==(255,255,255)
        if n==1: assert rgb.getpixel((100,100))==(0,0,0)
        if n>=3: assert len(rgb.getcolors(1000000))>colors//2
    assert total==400
    report.append({'palette':colors,'frames':im.n_frames,'duration_ms':total,'result':'PASS'})
print(json.dumps(report,indent=2))
