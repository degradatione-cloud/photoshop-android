//! Хостовый CLI для проверки парсера без Android:
//! `cargo run -p psd-jni --bin psdinfo -- <file.psd> <outdir>`

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 3 {
        eprintln!("usage: psdinfo <file.psd> <outdir>");
        std::process::exit(2);
    }
    match psd_jni::read_psd(std::path::Path::new(&args[1]), std::path::Path::new(&args[2])) {
        Ok(json) => println!("{json}"),
        Err(e) => {
            eprintln!("error: {e}");
            std::process::exit(1);
        }
    }
}
