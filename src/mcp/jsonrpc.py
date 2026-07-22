import json

from pydantic import BaseModel

"""
    把模型发来的json2rpc协议的json字段转换成python类,方便http方法调用
"""


class JsonRpcRequest(BaseModel):
    jsonrpc: str = "2.0"
    method: str
    params: dict | None
    id: str | int | None


def parse(raw_data: str) -> JsonRpcRequest:
    """HTTP body 字符串 -> 校验后的 JsonRpcRequest对象"""
    return JsonRpcRequest.model_validate_json(raw_data)


def build_response(result: dict, request_id: str | int | None = None) -> str:

    return json.dumps(
        {
            "jsonrpc": "2.0",
            "result": result,
            "id": request_id,
        },
        ensure_ascii=False,
    )


def build_error_response(
    code: int, message: str, request_id: str | int | None = None
) -> str:

    return json.dumps(
        {
            "jsonrpc": "2.0",
            "error": {
                "code": code,
                "message": message,
            },
            "id": request_id,
        },
        ensure_ascii=False,
    )
