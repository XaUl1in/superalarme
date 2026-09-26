"""Obtém apenas os scripts Tcl/Tk 9.0.4 ausentes no Python 3.14 deste PC."""
from pathlib import Path, PurePosixPath
import hashlib
import io
import urllib.request
import zipfile

DESTINATION = Path(__file__).resolve().parents[1] / "desktop" / "vendor"
CHECKSUMS = {
    "tcl": "e6a6d861b3fd75dd00c2c71b5b0f859022fa9b59207819a2417d93b9ab634f46",
    "tk": "e41bcf9d9ba318ac2975dffcd1ae4f89061f61649c7ed3bf4f5c362e5e6c24f0",
}


def main():
    for project, folder in [("tcl", "tcl9.0"), ("tk", "tk9.0")]:
        url = f"https://codeload.github.com/tcltk/{project}/zip/refs/tags/core-9-0-4"
        with urllib.request.urlopen(url, timeout=60) as response:
            archive = response.read()
        if hashlib.sha256(archive).hexdigest() != CHECKSUMS[project]:
            raise ValueError(f"Checksum inesperado para {project}; nenhum arquivo foi extraído.")
        target = DESTINATION / folder
        target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(io.BytesIO(archive)) as files:
            for item in files.infolist():
                parts = PurePosixPath(item.filename).parts
                if item.is_dir() or ".." in parts:
                    continue
                if len(parts) > 2 and parts[1] == "library":
                    output = target.joinpath(*parts[2:])
                elif len(parts) == 2 and parts[1] == "license.terms":
                    output = target / "license.terms"
                else:
                    continue
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(files.read(item))
        print(project, "9.0.4", "SHA-256", hashlib.sha256(archive).hexdigest())


if __name__ == "__main__":
    main()
