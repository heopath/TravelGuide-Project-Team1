/* QR 코드 생성기 (#265)
 *
 * 외부 CDN을 쓰지 않는 저장소라 직접 둔다. 필요한 범위만 구현했다.
 *
 *   - 바이트 모드, 오류정정 L, 버전 1~5
 *   - 이 범위는 블록이 하나뿐이라 인터리빙이 없다. 코드가 짧아지고 틀릴 자리도 줄어든다
 *   - 담을 수 있는 크기는 최대 106바이트. 입장 코드는 43자라 버전 3이면 된다
 *
 * 더 긴 값을 넣어야 하면 버전 6부터는 블록이 나뉘므로 인터리빙을 더해야 한다.
 * 지금 넣으면 쓰지도 않는 코드가 늘어날 뿐이라 두지 않았다.
 */

/* GF(256). 생성 다항식 x^8 + x^4 + x^3 + x^2 + 1 (0x11d) */
const EXP = new Uint8Array(512);
const LOG = new Uint8Array(256);
(function buildTables() {
    let x = 1;
    for (let i = 0; i < 255; i += 1) {
        EXP[i] = x;
        LOG[x] = i;
        x <<= 1;
        if (x & 0x100) x ^= 0x11d;
    }
    for (let i = 255; i < 512; i += 1) EXP[i] = EXP[i - 255];
})();

function gfMultiply(a, b) {
    if (a === 0 || b === 0) return 0;
    return EXP[LOG[a] + LOG[b]];
}

/** 오류정정 코드워드 개수만큼의 생성 다항식. */
function generatorPolynomial(degree) {
    let poly = [1];
    for (let i = 0; i < degree; i += 1) {
        const next = new Array(poly.length + 1).fill(0);
        for (let j = 0; j < poly.length; j += 1) {
            next[j] ^= poly[j];
            next[j + 1] ^= gfMultiply(poly[j], EXP[i]);
        }
        poly = next;
    }
    return poly;
}

function errorCorrection(data, ecCount) {
    const generator = generatorPolynomial(ecCount);
    const remainder = new Array(ecCount).fill(0);
    for (const byte of data) {
        const factor = byte ^ remainder[0];
        remainder.shift();
        remainder.push(0);
        for (let i = 0; i < ecCount; i += 1) {
            remainder[i] ^= gfMultiply(generator[i + 1], factor);
        }
    }
    return remainder;
}

/* 버전별 [전체 코드워드, 데이터 코드워드]. 오류정정 L 기준, 블록 1개인 범위만. */
const CAPACITY = {
    1: [26, 19],
    2: [44, 34],
    3: [70, 55],
    4: [100, 80],
    5: [134, 108],
};

/* 버전별 정렬 패턴 중심 좌표. 버전 1은 없다. */
const ALIGNMENT_CENTER = { 2: 18, 3: 22, 4: 26, 5: 30 };

function chooseVersion(byteLength) {
    /* 모드 4비트 + 길이 8비트 + 데이터. 8로 나눠 올림한 것이 필요한 코드워드다. */
    const needed = Math.ceil((4 + 8 + byteLength * 8) / 8);
    for (let version = 1; version <= 5; version += 1) {
        if (needed <= CAPACITY[version][1]) return version;
    }
    return null;
}

function encodeData(bytes, version) {
    const dataCount = CAPACITY[version][1];
    const bits = [];
    const push = (value, length) => {
        for (let i = length - 1; i >= 0; i -= 1) bits.push((value >> i) & 1);
    };

    push(0b0100, 4);          /* 바이트 모드 */
    push(bytes.length, 8);    /* 버전 1~9는 길이가 8비트 */
    bytes.forEach((byte) => push(byte, 8));

    /* 종단자. 남은 자리가 4비트보다 적으면 그만큼만 넣는다. */
    const capacityBits = dataCount * 8;
    for (let i = 0; i < 4 && bits.length < capacityBits; i += 1) bits.push(0);
    while (bits.length % 8 !== 0) bits.push(0);

    const codewords = [];
    for (let i = 0; i < bits.length; i += 8) {
        let byte = 0;
        for (let j = 0; j < 8; j += 1) byte = (byte << 1) | bits[i + j];
        codewords.push(byte);
    }
    /* 남는 자리는 규격이 정한 두 값을 번갈아 채운다. */
    const padding = [0xec, 0x11];
    for (let i = 0; codewords.length < dataCount; i += 1) {
        codewords.push(padding[i % 2]);
    }
    return codewords.concat(errorCorrection(codewords, CAPACITY[version][0] - dataCount));
}

function createMatrix(size) {
    const modules = [];
    const reserved = [];
    for (let row = 0; row < size; row += 1) {
        modules.push(new Array(size).fill(0));
        reserved.push(new Array(size).fill(false));
    }
    return { modules, reserved };
}

function placeFinder(matrix, size, row, column) {
    for (let r = -1; r <= 7; r += 1) {
        for (let c = -1; c <= 7; c += 1) {
            const y = row + r;
            const x = column + c;
            if (y < 0 || y >= size || x < 0 || x >= size) continue;
            const border = r === 0 || r === 6 || c === 0 || c === 6;
            const center = r >= 2 && r <= 4 && c >= 2 && c <= 4;
            matrix.modules[y][x] = border || center ? 1 : 0;
            matrix.reserved[y][x] = true;
        }
    }
}

function placeFunctionPatterns(matrix, size, version) {
    placeFinder(matrix, size, 0, 0);
    placeFinder(matrix, size, 0, size - 7);
    placeFinder(matrix, size, size - 7, 0);

    /* 타이밍 패턴. 6번 줄과 6번 칸을 한 칸씩 번갈아 채운다. */
    for (let i = 8; i < size - 8; i += 1) {
        const value = i % 2 === 0 ? 1 : 0;
        matrix.modules[6][i] = value;
        matrix.reserved[6][i] = true;
        matrix.modules[i][6] = value;
        matrix.reserved[i][6] = true;
    }

    const center = ALIGNMENT_CENTER[version];
    if (center) {
        for (let r = -2; r <= 2; r += 1) {
            for (let c = -2; c <= 2; c += 1) {
                const y = center + r;
                const x = center + c;
                const ring = Math.max(Math.abs(r), Math.abs(c));
                matrix.modules[y][x] = ring === 1 ? 0 : 1;
                matrix.reserved[y][x] = true;
            }
        }
    }

    /* 항상 검은 모듈 하나. 규격이 정한 자리다. */
    matrix.modules[size - 8][8] = 1;
    matrix.reserved[size - 8][8] = true;

    /* 형식 정보 자리를 미리 잡아둔다. 값은 마스크를 고른 뒤에 넣는다. */
    for (let i = 0; i <= 8; i += 1) {
        if (i !== 6) {
            matrix.reserved[8][i] = true;
            matrix.reserved[i][8] = true;
        }
    }
    for (let i = 0; i < 8; i += 1) {
        matrix.reserved[8][size - 1 - i] = true;
        matrix.reserved[size - 1 - i][8] = true;
    }
}

function placeData(matrix, size, codewords) {
    const bits = [];
    codewords.forEach((byte) => {
        for (let i = 7; i >= 0; i -= 1) bits.push((byte >> i) & 1);
    });

    let index = 0;
    let upward = true;
    for (let right = size - 1; right > 0; right -= 2) {
        /* 6번 칸은 타이밍 패턴이라 건너뛴다. 안 건너뛰면 이후 칸이 한 칸씩 밀린다. */
        if (right === 6) right -= 1;
        for (let step = 0; step < size; step += 1) {
            const row = upward ? size - 1 - step : step;
            for (let offset = 0; offset < 2; offset += 1) {
                const column = right - offset;
                if (matrix.reserved[row][column]) continue;
                matrix.modules[row][column] = index < bits.length ? bits[index] : 0;
                index += 1;
            }
        }
        upward = !upward;
    }
}

function maskCondition(pattern, row, column) {
    switch (pattern) {
        case 0: return (row + column) % 2 === 0;
        case 1: return row % 2 === 0;
        case 2: return column % 3 === 0;
        case 3: return (row + column) % 3 === 0;
        case 4: return (Math.floor(row / 2) + Math.floor(column / 3)) % 2 === 0;
        case 5: return ((row * column) % 2) + ((row * column) % 3) === 0;
        case 6: return (((row * column) % 2) + ((row * column) % 3)) % 2 === 0;
        default: return (((row + column) % 2) + ((row * column) % 3)) % 2 === 0;
    }
}

/* 규격의 감점 규칙 네 가지. 점수가 낮은 마스크가 읽기 좋다. */
function penalty(modules, size) {
    let score = 0;

    const runScore = (line) => {
        let run = 1;
        for (let i = 1; i < size; i += 1) {
            if (line[i] === line[i - 1]) {
                run += 1;
            } else {
                if (run >= 5) score += 3 + (run - 5);
                run = 1;
            }
        }
        if (run >= 5) score += 3 + (run - 5);
    };
    for (let i = 0; i < size; i += 1) {
        runScore(modules[i]);
        runScore(modules.map((row) => row[i]));
    }

    for (let row = 0; row < size - 1; row += 1) {
        for (let column = 0; column < size - 1; column += 1) {
            const value = modules[row][column];
            if (value === modules[row][column + 1]
                && value === modules[row + 1][column]
                && value === modules[row + 1][column + 1]) {
                score += 3;
            }
        }
    }

    /* 파인더를 닮은 배열은 스캐너가 위치 표식으로 오해한다. */
    const pattern = [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0];
    const reversed = pattern.slice().reverse();
    const matches = (line, start, target) => target.every((v, i) => line[start + i] === v);
    for (let i = 0; i < size; i += 1) {
        const rowLine = modules[i];
        const columnLine = modules.map((row) => row[i]);
        for (let start = 0; start + pattern.length <= size; start += 1) {
            if (matches(rowLine, start, pattern) || matches(rowLine, start, reversed)) score += 40;
            if (matches(columnLine, start, pattern) || matches(columnLine, start, reversed)) score += 40;
        }
    }

    let dark = 0;
    modules.forEach((row) => row.forEach((value) => { dark += value; }));
    const ratio = (dark * 100) / (size * size);
    score += Math.floor(Math.abs(ratio - 50) / 5) * 10;
    return score;
}

/* 형식 정보 15비트. 오류정정 L(01) + 마스크 3비트에 BCH를 붙이고 규격 마스크로 뒤집는다. */
function formatBits(maskPattern) {
    const data = (0b01 << 3) | maskPattern;
    let value = data << 10;
    for (let i = 4; i >= 0; i -= 1) {
        if ((value >> (i + 10)) & 1) value ^= 0b10100110111 << i;
    }
    return ((data << 10) | value) ^ 0b101010000010010;
}

function placeFormat(modules, size, maskPattern) {
    const bits = formatBits(maskPattern);
    const bit = (i) => (bits >> i) & 1;
    for (let i = 0; i <= 5; i += 1) {
        modules[8][i] = bit(i);
        modules[i][8] = bit(14 - i);
    }
    modules[8][7] = bit(6);
    modules[8][8] = bit(7);
    modules[7][8] = bit(8);
    for (let i = 9; i <= 14; i += 1) modules[14 - i][8] = bit(i);
    for (let i = 0; i < 8; i += 1) modules[8][size - 1 - i] = bit(i);
    for (let i = 8; i < 15; i += 1) modules[size - 1 - (14 - i)][8] = bit(i);
}

/**
 * 문자열을 QR 모듈 배열로 만든다.
 *
 * @returns {{size: number, modules: number[][]}}
 * @throws {Error} 담을 수 없을 만큼 긴 경우
 */
export function encodeQr(text) {
    const bytes = Array.from(new TextEncoder().encode(String(text)));
    const version = chooseVersion(bytes.length);
    if (!version) {
        throw new Error("QR로 담기에는 값이 너무 깁니다.");
    }

    const size = version * 4 + 17;
    const codewords = encodeData(bytes, version);

    let best = null;
    for (let maskPattern = 0; maskPattern < 8; maskPattern += 1) {
        const matrix = createMatrix(size);
        placeFunctionPatterns(matrix, size, version);
        placeData(matrix, size, codewords);
        for (let row = 0; row < size; row += 1) {
            for (let column = 0; column < size; column += 1) {
                if (matrix.reserved[row][column]) continue;
                if (maskCondition(maskPattern, row, column)) {
                    matrix.modules[row][column] ^= 1;
                }
            }
        }
        placeFormat(matrix.modules, size, maskPattern);
        const score = penalty(matrix.modules, size);
        if (!best || score < best.score) best = { score, modules: matrix.modules };
    }
    return { size, modules: best.modules };
}

/**
 * QR을 SVG 요소로 만든다.
 *
 * <p>canvas가 아니라 SVG인 이유는 확대해도 흐려지지 않기 때문이다. 현장에서 화면 밝기와
 * 각도가 나쁠 때 스캐너가 가장자리를 못 읽는 일이 줄어든다.
 */
export function createQrSvg(text, options = {}) {
    const { size, modules } = encodeQr(text);
    const quiet = options.quietZone ?? 4;   /* 규격이 요구하는 여백. 없으면 스캔이 잘 안 된다. */
    const total = size + quiet * 2;

    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", `0 0 ${total} ${total}`);
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", options.label || "입장 코드 QR");
    svg.style.width = "100%";
    svg.style.height = "auto";
    svg.style.display = "block";

    const background = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    background.setAttribute("width", String(total));
    background.setAttribute("height", String(total));
    background.setAttribute("fill", "#ffffff");
    svg.appendChild(background);

    /* 모듈마다 rect를 만들면 수백 개가 된다. 줄 단위로 이어 붙여 path 하나로 그린다. */
    let path = "";
    for (let row = 0; row < size; row += 1) {
        let column = 0;
        while (column < size) {
            if (!modules[row][column]) { column += 1; continue; }
            let run = 1;
            while (column + run < size && modules[row][column + run]) run += 1;
            path += `M${column + quiet} ${row + quiet}h${run}v1h-${run}z`;
            column += run;
        }
    }
    const shape = document.createElementNS("http://www.w3.org/2000/svg", "path");
    shape.setAttribute("d", path);
    shape.setAttribute("fill", "#0b1b3f");
    svg.appendChild(shape);
    return svg;
}
