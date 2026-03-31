use crate::config::{AppConfig, FlowKey, CONFIG_SCHEMA_VERSION, CORE_VERSION};
use crate::engine::{Engine, FlowResultReport};
use once_cell::sync::Lazy;
use serde_json::json;
use std::collections::HashMap;
use std::ffi::{c_char, c_uchar, CStr, CString};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Mutex;

static NEXT_ENGINE_ID: AtomicU64 = AtomicU64::new(1);
static ENGINES: Lazy<Mutex<HashMap<u64, Engine>>> = Lazy::new(|| Mutex::new(HashMap::new()));

fn into_c_string(value: String) -> *mut c_char {
    CString::new(value).expect("json response must not contain nul").into_raw()
}

fn parse_json_input(input: *const c_char) -> Result<String, String> {
    if input.is_null() {
        return Err("null input".to_string());
    }
    let c_str = unsafe { CStr::from_ptr(input) };
    c_str
        .to_str()
        .map(|value| value.to_string())
        .map_err(|error| error.to_string())
}

#[no_mangle]
pub extern "C" fn get_core_version() -> *mut c_char {
    into_c_string(json!({ "coreVersion": CORE_VERSION }).to_string())
}

#[no_mangle]
pub extern "C" fn get_schema_version() -> u32 {
    CONFIG_SCHEMA_VERSION
}

#[no_mangle]
pub extern "C" fn validate_config(config_json: *const c_char) -> *mut c_char {
    let response = match parse_json_input(config_json)
        .and_then(|json| AppConfig::from_json(&json).map_err(|error| error.to_string()))
    {
        Ok(config) => json!({
            "ok": true,
            "activeProfileId": config.active_profile_id,
            "schemaVersion": config.config_schema_version
        }),
        Err(error) => json!({ "ok": false, "error": error }),
    };
    into_c_string(response.to_string())
}

#[no_mangle]
pub extern "C" fn load_config(config_json: *const c_char) -> *mut c_char {
    let response = match parse_json_input(config_json)
        .and_then(|json| AppConfig::from_json(&json).map_err(|error| error.to_string()))
    {
        Ok(config) => {
            let engine_id = NEXT_ENGINE_ID.fetch_add(1, Ordering::Relaxed);
            ENGINES
                .lock()
                .expect("engine lock poisoned")
                .insert(engine_id, Engine::new(config));
            json!({ "ok": true, "engineHandle": engine_id })
        }
        Err(error) => json!({ "ok": false, "error": error }),
    };
    into_c_string(response.to_string())
}

#[no_mangle]
pub extern "C" fn free_engine(engine_handle: u64) -> bool {
    ENGINES
        .lock()
        .expect("engine lock poisoned")
        .remove(&engine_handle)
        .is_some()
}

#[no_mangle]
pub extern "C" fn inspect_early_bytes(
    engine_handle: u64,
    flow_key_json: *const c_char,
    buffered_bytes: *const c_uchar,
    buffered_len: usize,
    now_ms: u64,
) -> *mut c_char {
    let response = (|| -> Result<serde_json::Value, String> {
        let flow_key_json = parse_json_input(flow_key_json)?;
        let flow_key: FlowKey =
            serde_json::from_str(&flow_key_json).map_err(|error| error.to_string())?;
        if buffered_bytes.is_null() && buffered_len > 0 {
            return Err("buffer pointer was null".to_string());
        }
        let bytes = if buffered_len == 0 {
            &[][..]
        } else {
            unsafe { std::slice::from_raw_parts(buffered_bytes, buffered_len) }
        };
        let guard = ENGINES.lock().expect("engine lock poisoned");
        let Some(engine) = guard.get(&engine_handle) else {
            return Err(format!("unknown engine handle: {engine_handle}"));
        };
        serde_json::to_value(engine.inspect_early_bytes(&flow_key, bytes, now_ms))
            .map_err(|error| error.to_string())
    })()
    .unwrap_or_else(|error| json!({ "state": "FINAL", "action": "DIRECT", "error": error }));

    into_c_string(response.to_string())
}

#[no_mangle]
pub extern "C" fn report_flow_result(engine_handle: u64, report_json: *const c_char) -> bool {
    let Ok(report_json) = parse_json_input(report_json) else {
        return false;
    };
    let Ok(report) = serde_json::from_str::<FlowResultReport>(&report_json) else {
        return false;
    };
    let mut guard = ENGINES.lock().expect("engine lock poisoned");
    let Some(engine) = guard.get_mut(&engine_handle) else {
        return false;
    };
    engine.report_flow_result(report);
    true
}

#[no_mangle]
pub extern "C" fn healthcheck(config_json: *const c_char) -> *mut c_char {
    let response = match parse_json_input(config_json)
        .and_then(|json| AppConfig::from_json(&json).map_err(|error| error.to_string()))
    {
        Ok(config) => {
            let engine = Engine::new(config);
            let profile = engine.config().active_profile();
            json!({
                "ok": true,
                "activeProfileId": profile.id,
                "preset": format!("{:?}", profile.preset),
                "quicPolicy": format!("{:?}", profile.quic_policy)
            })
        }
        Err(error) => json!({ "ok": false, "error": error }),
    };
    into_c_string(response.to_string())
}

#[no_mangle]
pub extern "C" fn free_rust_string(value: *mut c_char) {
    if value.is_null() {
        return;
    }
    unsafe {
        drop(CString::from_raw(value));
    }
}
