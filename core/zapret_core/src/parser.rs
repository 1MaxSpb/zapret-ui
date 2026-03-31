use crate::config::FeatureFlags;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TrafficKind {
    Http,
    Tls,
    Unknown,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExtractOutcome {
    pub hostname: Option<String>,
    pub needs_more_data: bool,
    pub kind: TrafficKind,
}

pub fn extract_target(data: &[u8], flags: &FeatureFlags) -> ExtractOutcome {
    if flags.enable_http_host && looks_like_http(data) {
        return extract_http_host(data);
    }
    if flags.enable_tls_sni && looks_like_tls_client_hello(data) {
        return extract_tls_sni(data);
    }

    ExtractOutcome {
        hostname: None,
        needs_more_data: data.len() < 32,
        kind: TrafficKind::Unknown,
    }
}

fn looks_like_http(data: &[u8]) -> bool {
    const METHODS: [&[u8]; 6] = [
        b"GET ",
        b"POST ",
        b"HEAD ",
        b"PUT ",
        b"DELETE ",
        b"CONNECT ",
    ];
    METHODS.iter().any(|method| data.starts_with(method))
}

fn extract_http_host(data: &[u8]) -> ExtractOutcome {
    let header_end = data.windows(4).position(|window| window == b"\r\n\r\n");
    let Some(header_end) = header_end else {
        return ExtractOutcome {
            hostname: None,
            needs_more_data: true,
            kind: TrafficKind::Http,
        };
    };

    let headers = &data[..header_end + 4];
    let header_text = String::from_utf8_lossy(headers);
    let hostname = header_text.lines().find_map(|line| {
        let (name, value) = line.split_once(':')?;
        if name.eq_ignore_ascii_case("host") {
            Some(value.trim().to_string())
        } else {
            None
        }
    });

    ExtractOutcome {
        hostname,
        needs_more_data: false,
        kind: TrafficKind::Http,
    }
}

fn looks_like_tls_client_hello(data: &[u8]) -> bool {
    data.len() >= 5 && data[0] == 0x16 && data[1] == 0x03
}

fn extract_tls_sni(data: &[u8]) -> ExtractOutcome {
    if data.len() < 5 {
        return tls_truncated();
    }

    let record_len = u16::from_be_bytes([data[3], data[4]]) as usize;
    if data.len() < 5 + record_len {
        return tls_truncated();
    }

    let mut cursor = 5;
    if data.get(cursor) != Some(&0x01) {
        return ExtractOutcome {
            hostname: None,
            needs_more_data: false,
            kind: TrafficKind::Tls,
        };
    }
    cursor += 4;
    cursor += 2;
    cursor += 32;
    let Some(session_len) = data.get(cursor).copied() else {
        return tls_truncated();
    };
    cursor += 1 + session_len as usize;

    let Some(cipher_len_bytes) = data.get(cursor..cursor + 2) else {
        return tls_truncated();
    };
    let cipher_len = u16::from_be_bytes([cipher_len_bytes[0], cipher_len_bytes[1]]) as usize;
    cursor += 2 + cipher_len;

    let Some(compression_len) = data.get(cursor).copied() else {
        return tls_truncated();
    };
    cursor += 1 + compression_len as usize;

    let Some(extension_len_bytes) = data.get(cursor..cursor + 2) else {
        return tls_truncated();
    };
    let extension_len =
        u16::from_be_bytes([extension_len_bytes[0], extension_len_bytes[1]]) as usize;
    cursor += 2;
    let extension_end = cursor + extension_len;
    if extension_end > data.len() {
        return tls_truncated();
    }

    while cursor + 4 <= extension_end {
        let extension_type = u16::from_be_bytes([data[cursor], data[cursor + 1]]);
        let extension_size = u16::from_be_bytes([data[cursor + 2], data[cursor + 3]]) as usize;
        cursor += 4;
        let ext_end = cursor + extension_size;
        if ext_end > extension_end {
            return tls_truncated();
        }
        if extension_type == 0x0000 {
            let Some(list_len_bytes) = data.get(cursor..cursor + 2) else {
                return tls_truncated();
            };
            let server_name_list_len =
                u16::from_be_bytes([list_len_bytes[0], list_len_bytes[1]]) as usize;
            let mut name_cursor = cursor + 2;
            let name_end = name_cursor + server_name_list_len;
            if name_end > ext_end {
                return tls_truncated();
            }
            while name_cursor + 3 <= name_end {
                let name_type = data[name_cursor];
                let name_len =
                    u16::from_be_bytes([data[name_cursor + 1], data[name_cursor + 2]]) as usize;
                name_cursor += 3;
                if name_cursor + name_len > name_end {
                    return tls_truncated();
                }
                if name_type == 0 {
                    let hostname =
                        String::from_utf8_lossy(&data[name_cursor..name_cursor + name_len]).to_string();
                    return ExtractOutcome {
                        hostname: Some(hostname),
                        needs_more_data: false,
                        kind: TrafficKind::Tls,
                    };
                }
                name_cursor += name_len;
            }
        }
        cursor = ext_end;
    }

    ExtractOutcome {
        hostname: None,
        needs_more_data: false,
        kind: TrafficKind::Tls,
    }
}

fn tls_truncated() -> ExtractOutcome {
    ExtractOutcome {
        hostname: None,
        needs_more_data: true,
        kind: TrafficKind::Tls,
    }
}
