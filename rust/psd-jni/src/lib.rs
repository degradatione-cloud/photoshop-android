//! JNI-мост к PSD-парсеру PhotoCraft (vendored crate `photocraft-psd`).
//!
//! Наружу отдаёт один вызов: путь к .psd + папка для PNG → JSON с метаданными
//! документа и слоёв. Пиксели каждого слоя сохраняются отдельными PNG, чтобы
//! Kotlin-сторона просто грузила их в Bitmap.

use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use photocraft_psd::PsdFile;

fn json_escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 8);
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

fn write_png(path: &Path, width: u32, height: u32, data: &[u8]) -> Result<(), String> {
    if width == 0 || height == 0 {
        return Err("пустой слой".to_string());
    }
    let expect = (width as usize)
        .checked_mul(height as usize)
        .and_then(|n| n.checked_mul(4))
        .ok_or_else(|| "переполнение размера слоя".to_string())?;
    if data.len() < expect {
        return Err(format!("RGBA короче ожидаемого: {} < {}", data.len(), expect));
    }
    let file = File::create(path).map_err(|e| format!("create {}: {e}", path.display()))?;
    let mut w = BufWriter::new(file);
    let mut enc = png::Encoder::new(&mut w, width, height);
    enc.set_color(png::ColorType::Rgba);
    enc.set_depth(png::BitDepth::Eight);
    let mut writer = enc.write_header().map_err(|e| format!("png header: {e}"))?;
    writer
        .write_image_data(&data[..expect])
        .map_err(|e| format!("png data: {e}"))?;
    drop(writer);
    w.flush().map_err(|e| format!("flush: {e}"))?;
    Ok(())
}

fn blend_name(mode: photocraft_psd::blend::BlendMode) -> String {
    format!("{:?}", mode)
}

/// Разбирает PSD и раскладывает слои в PNG. Возвращает JSON-строку.
pub fn read_psd(path: &Path, out_dir: &Path) -> Result<String, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("read {}: {e}", path.display()))?;
    let file = PsdFile::from_bytes(&bytes).map_err(|e| format!("разбор PSD: {e}"))?;
    std::fs::create_dir_all(out_dir).map_err(|e| format!("mkdir: {e}"))?;

    let w = file.header.width;
    let h = file.header.height;
    let mut meta = String::new();
    let mut saved = 0usize;

    let count = file.layers().len();
    for i in 0..count {
        let Some(layer) = file.layer(i) else { continue };
        let record = &file.layers()[i];
        let rect = record.rect;
        let (lw, lh) = match rect.size() {
            Ok(v) => v,
            Err(_) => continue,
        };
        if lw == 0 || lh == 0 {
            continue;
        }
        let png_path: PathBuf = out_dir.join(format!("psdlayer_{i}.png"));
        let mut written = false;
        if let Ok(img) = layer.rgba8() {
            if write_png(&png_path, img.width, img.height, &img.data).is_ok() {
                written = true;
            }
        }
        if !meta.is_empty() {
            meta.push(',');
        }
        meta.push_str(&format!(
            "{{\"index\":{i},\"name\":\"{}\",\"x\":{},\"y\":{},\"w\":{},\"h\":{},\
             \"opacity\":{:.3},\"blend\":\"{}\",\"visible\":{},\"png\":{}}}",
            json_escape(&record.name()),
            rect.left,
            rect.top,
            lw,
            lh,
            record.opacity as f32 / 255.0,
            blend_name(record.blend_mode),
            !record.flags.hidden(),
            if written {
                format!("\"{}\"", json_escape(&png_path.display().to_string()))
            } else {
                "null".to_string()
            }
        ));
        if written {
            saved += 1;
        }
    }

    Ok(format!(
        "{{\"ok\":true,\"width\":{w},\"height\":{h},\"depth\":{},\"layers\":[{meta}],\"saved\":{saved}}}",
        file.header.depth
    ))
}

#[no_mangle]
pub extern "system" fn Java_com_degradatione_psandroid_PsdBridge_nativeReadPsd(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    out_dir: JString,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let path: String = env
            .get_string(&path)
            .map_err(|e| format!("path: {e}"))?
            .into();
        let out_dir: String = env
            .get_string(&out_dir)
            .map_err(|e| format!("out_dir: {e}"))?
            .into();
        read_psd(Path::new(&path), Path::new(&out_dir))
    })();

    let payload = match result {
        Ok(json) => json,
        Err(err) => format!("{{\"ok\":false,\"error\":\"{}\"}}", json_escape(&err)),
    };
    match env.new_string(payload) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_degradatione_psandroid_PsdBridge_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let s = format!("psd-core {}", env!("CARGO_PKG_VERSION"));
    match env.new_string(s) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
