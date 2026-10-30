//! Генерирует тестовый многослойный PSD для проверки парсера:
//! `cargo run -p psd-jni --bin mkpsd -- /tmp/test.psd`

use photocraft_psd::{LayerSpec, PixelData, PsdBuilder};

fn main() {
    let out = std::env::args().nth(1).unwrap_or_else(|| "/tmp/test.psd".to_string());
    let (w, h) = (300u32, 200u32);
    let mut builder = PsdBuilder::new(w, h);

    // фон: синий градиент
    let mut bg = Vec::with_capacity((w * h * 4) as usize);
    for y in 0..h {
        for x in 0..w {
            bg.push((40 + (x % 80)) as u8);
            bg.push(60);
            bg.push(160 + (y % 60) as u8);
            bg.push(255);
        }
    }
    builder.push_layer(LayerSpec::new("Background", 0, 0, w, h, PixelData::Rgba8(bg.clone())));

    // полупрозрачный красный квадрат в центре
    let (sw, sh) = (120u32, 120u32);
    let mut sq = Vec::with_capacity((sw * sh * 4) as usize);
    for _ in 0..(sw * sh) {
        sq.extend_from_slice(&[220, 40, 40, 180]);
    }
    builder.push_layer(LayerSpec::new(
        "Red square",
        ((w - sw) / 2) as i32,
        ((h - sh) / 2) as i32,
        sw,
        sh,
        PixelData::Rgba8(sq),
    ));

    builder.composite(PixelData::Rgba8(bg));
    let bytes = builder.to_bytes().expect("build psd");
    std::fs::write(&out, &bytes).expect("write psd");
    println!("wrote {} ({} bytes, {}×{})", out, bytes.len(), w, h);
}
