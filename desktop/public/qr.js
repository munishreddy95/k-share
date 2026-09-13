(function () {
  const VERSION = 5;
  const SIZE = VERSION * 4 + 17;
  const DATA_CODEWORDS = 108;
  const EC_CODEWORDS = 26;
  const EXP = new Array(512);
  const LOG = new Array(256);

  let value = 1;
  for (let i = 0; i < 255; i++) {
    EXP[i] = value;
    LOG[value] = i;
    value <<= 1;
    if (value & 0x100) {
      value ^= 0x11d;
    }
  }
  for (let i = 255; i < EXP.length; i++) {
    EXP[i] = EXP[i - 255];
  }

  function gfMultiply(a, b) {
    return a === 0 || b === 0 ? 0 : EXP[LOG[a] + LOG[b]];
  }

  function rsGenerator(degree) {
    const polynomial = new Array(degree).fill(0);
    polynomial[degree - 1] = 1;
    let root = 1;
    for (let i = 0; i < degree; i++) {
      for (let j = 0; j < degree; j++) {
        polynomial[j] = gfMultiply(polynomial[j], root);
        if (j + 1 < degree) {
          polynomial[j] ^= polynomial[j + 1];
        }
      }
      root = gfMultiply(root, 2);
    }
    return polynomial;
  }

  const RS_GENERATOR = rsGenerator(EC_CODEWORDS);

  function reedSolomon(data) {
    const result = new Array(EC_CODEWORDS).fill(0);
    for (const byte of data) {
      const factor = byte ^ result.shift();
      result.push(0);
      for (let i = 0; i < EC_CODEWORDS; i++) {
        result[i] ^= gfMultiply(RS_GENERATOR[i], factor);
      }
    }
    return result;
  }

  function pushBits(bits, number, width) {
    for (let i = width - 1; i >= 0; i--) {
      bits.push((number >>> i) & 1);
    }
  }

  function encodeText(text) {
    const bytes = Array.from(new TextEncoder().encode(text));
    if (bytes.length > 106) {
      throw new Error("QR payload is too long");
    }
    const bits = [];
    pushBits(bits, 0b0100, 4);
    pushBits(bits, bytes.length, 8);
    for (const byte of bytes) {
      pushBits(bits, byte, 8);
    }
    const capacityBits = DATA_CODEWORDS * 8;
    for (let i = 0; i < 4 && bits.length < capacityBits; i++) {
      bits.push(0);
    }
    while (bits.length % 8 !== 0) {
      bits.push(0);
    }
    const data = [];
    for (let i = 0; i < bits.length; i += 8) {
      let byte = 0;
      for (let j = 0; j < 8; j++) {
        byte = (byte << 1) | bits[i + j];
      }
      data.push(byte);
    }
    for (let pad = 0; data.length < DATA_CODEWORDS; pad++) {
      data.push(pad % 2 === 0 ? 0xec : 0x11);
    }
    return data.concat(reedSolomon(data));
  }

  function createMatrix() {
    const modules = Array.from({ length: SIZE }, () => new Array(SIZE).fill(false));
    const reserved = Array.from({ length: SIZE }, () => new Array(SIZE).fill(false));
    return { modules, reserved };
  }

  function setModule(matrix, x, y, dark, reserve = true) {
    if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
      return;
    }
    matrix.modules[y][x] = !!dark;
    if (reserve) {
      matrix.reserved[y][x] = true;
    }
  }

  function drawFinder(matrix, left, top) {
    for (let y = -1; y <= 7; y++) {
      for (let x = -1; x <= 7; x++) {
        const xx = left + x;
        const yy = top + y;
        if (xx < 0 || yy < 0 || xx >= SIZE || yy >= SIZE) {
          continue;
        }
        const dark = (x >= 0 && x <= 6 && y >= 0 && y <= 6)
          && (x === 0 || x === 6 || y === 0 || y === 6 || (x >= 2 && x <= 4 && y >= 2 && y <= 4));
        setModule(matrix, xx, yy, dark);
      }
    }
  }

  function drawAlignment(matrix, centerX, centerY) {
    for (let y = -2; y <= 2; y++) {
      for (let x = -2; x <= 2; x++) {
        const dark = Math.max(Math.abs(x), Math.abs(y)) !== 1;
        setModule(matrix, centerX + x, centerY + y, dark);
      }
    }
  }

  function drawFunctionPatterns(matrix) {
    drawFinder(matrix, 0, 0);
    drawFinder(matrix, SIZE - 7, 0);
    drawFinder(matrix, 0, SIZE - 7);
    drawAlignment(matrix, 30, 30);
    for (let i = 8; i < SIZE - 8; i++) {
      const dark = i % 2 === 0;
      setModule(matrix, i, 6, dark);
      setModule(matrix, 6, i, dark);
    }
    setModule(matrix, 8, VERSION * 4 + 9, true);
    reserveFormatAreas(matrix);
  }

  function reserveFormatAreas(matrix) {
    for (let i = 0; i <= 8; i++) {
      if (i !== 6) {
        setModule(matrix, 8, i, false);
        setModule(matrix, i, 8, false);
      }
    }
    for (let i = 0; i < 8; i++) {
      setModule(matrix, SIZE - 1 - i, 8, false);
      setModule(matrix, 8, SIZE - 1 - i, false);
    }
  }

  function maskBit(mask, x, y) {
    if (mask === 0) return (x + y) % 2 === 0;
    if (mask === 1) return y % 2 === 0;
    if (mask === 2) return x % 3 === 0;
    return (x + y) % 3 === 0;
  }

  function drawCodewords(matrix, codewords, mask) {
    const bits = [];
    for (const byte of codewords) {
      pushBits(bits, byte, 8);
    }
    let bitIndex = 0;
    let upward = true;
    for (let right = SIZE - 1; right >= 1; right -= 2) {
      if (right === 6) {
        right--;
      }
      for (let vertical = 0; vertical < SIZE; vertical++) {
        const y = upward ? SIZE - 1 - vertical : vertical;
        for (let column = 0; column < 2; column++) {
          const x = right - column;
          if (matrix.reserved[y][x]) {
            continue;
          }
          const bit = (bits[bitIndex++] || 0) === 1;
          matrix.modules[y][x] = bit !== maskBit(mask, x, y);
        }
      }
      upward = !upward;
    }
  }

  function formatBits(mask) {
    const errorCorrectionLow = 1;
    const data = (errorCorrectionLow << 3) | mask;
    let bits = data << 10;
    for (let i = 14; i >= 10; i--) {
      if (((bits >>> i) & 1) !== 0) {
        bits ^= 0x537 << (i - 10);
      }
    }
    return ((data << 10) | bits) ^ 0x5412;
  }

  function getBit(value, index) {
    return ((value >>> index) & 1) !== 0;
  }

  function drawFormatBits(matrix, mask) {
    const bits = formatBits(mask);
    for (let i = 0; i <= 5; i++) setModule(matrix, 8, i, getBit(bits, i));
    setModule(matrix, 8, 7, getBit(bits, 6));
    setModule(matrix, 8, 8, getBit(bits, 7));
    setModule(matrix, 7, 8, getBit(bits, 8));
    for (let i = 9; i < 15; i++) setModule(matrix, 14 - i, 8, getBit(bits, i));
    for (let i = 0; i < 8; i++) setModule(matrix, SIZE - 1 - i, 8, getBit(bits, i));
    for (let i = 8; i < 15; i++) setModule(matrix, 8, SIZE - 15 + i, getBit(bits, i));
    setModule(matrix, 8, SIZE - 8, true);
  }

  function toSvg(text, options = {}) {
    const codewords = encodeText(text);
    const matrix = createMatrix();
    const mask = 2;
    drawFunctionPatterns(matrix);
    drawCodewords(matrix, codewords, mask);
    drawFormatBits(matrix, mask);
    const quiet = options.quiet == null ? 4 : options.quiet;
    const scale = options.scale || 8;
    const size = (SIZE + quiet * 2) * scale;
    const rects = [];
    for (let y = 0; y < SIZE; y++) {
      for (let x = 0; x < SIZE; x++) {
        if (matrix.modules[y][x]) {
          rects.push(`<rect x="${(x + quiet) * scale}" y="${(y + quiet) * scale}" width="${scale}" height="${scale}"/>`);
        }
      }
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" role="img" aria-label="Pairing QR code"><rect width="${size}" height="${size}" rx="${scale * 2}" fill="#fff"/><g fill="#071013">${rects.join("")}</g></svg>`;
  }

  window.FluxQr = { toSvg };
})();
