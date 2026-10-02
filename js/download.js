// Browser downloads for generated files.

export function downloadFile(filename, content, mime) {
    const blob = new Blob([content], { type: mime });
    downloadBlob(filename, blob);
}

export function downloadBlob(filename, blob) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
        URL.revokeObjectURL(url);
        a.remove();
    }, 100);
}
