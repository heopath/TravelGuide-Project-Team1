# README에 넣을 GIF

메인 README 위쪽에서 쓰는 화면 GIF를 여기 둔다.

| 파일 | 담은 내용 | 잘라낸 구간 |
|---|---|---|
| `booking-to-checkin.gif` | 결제 → 입장용 QR 발급 → 현장 검표 → 입장 승인 | 8:17 · 8:23 · 8:49 · 8:58 (각 3초) |
| `record-book.gif` | 책 지면이 넘어가며 배치가 바뀌는 것 | 12:50 ~ 12:59 |
| `record-book.png` | 지면 한 장. README에는 안 쓴다. 보고서·위키용 | 12:52 |

[시연 영상](https://youtu.be/HHS_6rQ8duA)에서 `ffmpeg`으로 잘라냈다.

```bash
# 한 구간짜리 — 12:50부터 9.5초, 책 영역만 잘라서
FILT="crop=848:608:516:222,scale=560:-2:flags=lanczos,fps=10"
ffmpeg -ss 770 -t 9.5 -i 원본.mp4 -vf "$FILT,palettegen=max_colors=128:stats_mode=diff" -y pal.png
ffmpeg -ss 770 -t 9.5 -i 원본.mp4 -i pal.png -lavfi "$FILT[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=3" -y out.gif
```

여러 구간을 이어 붙일 때는 구간별로 mp4를 뽑아 `concat`으로 합친 뒤 한 번에 GIF로 바꾼다.

**팔레트를 따로 뽑는 이유**는 GIF가 256색까지만 쓸 수 있어서다. 기본 팔레트로 바꾸면
사진이 뭉개진다. 그 구간에 실제로 쓰인 색으로 팔레트를 만들면 크기도 줄고 화질도 낫다.

- 폭 840px 안팎이면 README에서 두 개를 나란히 놓기 좋다
- 소리는 없으니 자막이나 강조가 없으면 무슨 장면인지 알기 어렵다.
  위 두 GIF는 **영상에 박힌 자막을 그대로 살려** 무슨 장면인지 알 수 있게 했다
- 파일이 크면 저장소가 무거워진다. 10초 안팎, 5MB 아래로 맞춘다
