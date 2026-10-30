//! Проверка записи PSD: `psdwrite <manifest.json> <out.psd>`

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 3 {
        eprintln!("usage: psdwrite <manifest.json> <out.psd>");
        std::process::exit(2);
    }
    let manifest = std::fs::read_to_string(&args[1]).expect("manifest");
    match psd_jni::write_psd(&manifest, std::path::Path::new(&args[2])) {
        Ok(json) => println!("{json}"),
        Err(e) => {
            eprintln!("error: {e}");
            std::process::exit(1);
        }
    }
}
