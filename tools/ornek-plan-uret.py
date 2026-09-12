# -*- coding: utf-8 -*-
"""Bilinen-iyi bir kat planı DXF'i üretir.

Amaç: cihazda çizim ekranını denerken dosyanın kendisi şüpheli olmasın.
Gerçek bir CAD çıktısında ne varsa burada da var — katmanlar, kapalı duvar
çokgenleri, hazır parça (blok) olarak yerleştirilmiş kapılar (döndürülmüş ve
aynalanmış olanlar dahil), pencereler, mobilya ve oda yazıları.
"""

g = []
def p(code, value):
    g.append(str(code))
    g.append(str(value))

def line(layer, x1, y1, x2, y2):
    p(0, "LINE"); p(8, layer)
    p(10, f"{x1:.1f}"); p(20, f"{y1:.1f}"); p(30, "0.0")
    p(11, f"{x2:.1f}"); p(21, f"{y2:.1f}"); p(31, "0.0")

def polyline(layer, pts, closed=True):
    p(0, "LWPOLYLINE"); p(8, layer); p(90, len(pts)); p(70, 1 if closed else 0)
    for (x, y) in pts:
        p(10, f"{x:.1f}"); p(20, f"{y:.1f}")

def circle(layer, cx, cy, r):
    p(0, "CIRCLE"); p(8, layer)
    p(10, f"{cx:.1f}"); p(20, f"{cy:.1f}"); p(30, "0.0"); p(40, f"{r:.1f}")

def text(layer, x, y, height, value, centred=True):
    p(0, "TEXT"); p(8, layer)
    p(10, f"{x:.1f}"); p(20, f"{y:.1f}"); p(30, "0.0")
    p(40, f"{height:.1f}"); p(1, value)
    p(72, 1 if centred else 0)

def insert(layer, name, x, y, rot=0.0, sx=1.0, sy=1.0):
    p(0, "INSERT"); p(8, layer); p(2, name)
    p(10, f"{x:.1f}"); p(20, f"{y:.1f}"); p(30, "0.0")
    p(41, f"{sx:.3f}"); p(42, f"{sy:.3f}"); p(43, "1.000")
    p(50, f"{rot:.1f}")

def rect(layer, x, y, w, h):
    polyline(layer, [(x, y), (x + w, y), (x + w, y + h), (x, y + h)])

W, H, T = 12000.0, 9000.0, 200.0   # dış ölçüler ve duvar kalınlığı

# ---- HEADER: birim milimetre
p(0, "SECTION"); p(2, "HEADER")
p(9, "$ACADVER"); p(1, "AC1009")
p(9, "$INSUNITS"); p(70, "4")
p(0, "ENDSEC")

# ---- TABLES: katmanlar
p(0, "SECTION"); p(2, "TABLES")
p(0, "TABLE"); p(2, "LAYER"); p(70, "6")
for name, colour in [("0", 7), ("DUVAR", 7), ("KAPI", 3), ("PENCERE", 5),
                     ("MOBILYA", 8), ("METIN", 2)]:
    p(0, "LAYER"); p(2, name); p(70, "0"); p(62, str(colour)); p(6, "CONTINUOUS")
p(0, "ENDTAB"); p(0, "ENDSEC")

# ---- BLOCKS: bir kapı sembolü, kendi başlangıç noktası etrafında
p(0, "SECTION"); p(2, "BLOCKS")
p(0, "BLOCK"); p(2, "KAPI-90"); p(10, "0.0"); p(20, "0.0"); p(30, "0.0")
line("0", 0.0, 0.0, 900.0, 0.0)                      # kapı kanadı
p(0, "ARC"); p(8, "0")
p(10, "0.0"); p(20, "0.0"); p(30, "0.0"); p(40, "900.0")
p(50, "0.0"); p(51, "90.0")                          # açılma yayı
p(0, "ENDBLK")
p(0, "ENDSEC")

# ---- ENTITIES
p(0, "SECTION"); p(2, "ENTITIES")

# Dış duvar: iki kapalı çokgen (dış ve iç yüz)
polyline("DUVAR", [(0, 0), (W, 0), (W, H), (0, H)])
polyline("DUVAR", [(T, T), (W - T, T), (W - T, H - T), (T, H - T)])

# İç duvarlar (her biri çift çizgi)
def ic_duvar(x1, y1, x2, y2):
    if abs(y2 - y1) < 1:      # yatay
        line("DUVAR", x1, y1, x2, y1)
        line("DUVAR", x1, y1 + T, x2, y1 + T)
    else:                      # düşey
        line("DUVAR", x1, y1, x1, y2)
        line("DUVAR", x1 + T, y1, x1 + T, y2)

ic_duvar(5000, T, 5000, 5200)          # salon / mutfak ayrımı
ic_duvar(T, 5200, 5200, 5200)          # koridor üstü
ic_duvar(8000, 5400, 8000, H - T)      # yatak odası ayrımı
ic_duvar(5200, 5400, 8000, 5400)       # banyo duvarı

# Kapılar: döndürülmüş ve aynalanmış yerleştirmeler
insert("KAPI", "KAPI-90", 5200.0, 1200.0, rot=90.0)
insert("KAPI", "KAPI-90", 2400.0, 5200.0, rot=0.0)
insert("KAPI", "KAPI-90", 7800.0, 6200.0, rot=180.0, sx=1.0, sy=-1.0)
insert("KAPI", "KAPI-90", 6200.0, 5600.0, rot=90.0, sx=-1.0, sy=1.0)

# Pencereler: duvar boşluğunda üç çizgi
def pencere(x, y, uzunluk, yatay=True):
    if yatay:
        for dy in (0.0, T / 2, T):
            line("PENCERE", x, y + dy, x + uzunluk, y + dy)
    else:
        for dx in (0.0, T / 2, T):
            line("PENCERE", x + dx, y, x + dx, y + uzunluk)

pencere(1200, 0, 2400)
pencere(8400, 0, 2400)
pencere(0, 6200, 1800, yatay=False)
pencere(3000, H - T, 2000)

# Mobilya
rect("MOBILYA", 8600, 6400, 2000, 1600)        # yatak
circle("MOBILYA", 2500, 2400, 700)             # yemek masası
rect("MOBILYA", 5400, 600, 3000, 600)          # mutfak tezgahı
rect("MOBILYA", 5400, 5600, 700, 1400)         # küvet

# Oda yazıları (yalnızca ASCII harfler: dosya kodlaması sorun çıkarmasın)
text("METIN", 2600, 2600, 300, "SALON")
text("METIN", 8600, 2600, 300, "MUTFAK")
text("METIN", 6400, 6400, 300, "BANYO")
text("METIN", 9800, 7200, 300, "YATAK ODASI")
text("METIN", 2600, 6000, 250, "ANTRE")
text("METIN", 6000, 250, 200, "PAFTA ORNEK KAT PLANI 1:50")

p(0, "ENDSEC")
p(0, "EOF")

out = "/home/user/PAFTA/ornek/kat-plani.dxf"
with open(out, "w", encoding="ascii", newline="\r\n") as f:
    f.write("\n".join(g) + "\n")
print("yazıldı:", out, len(g) // 2, "grup")
