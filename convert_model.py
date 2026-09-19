import onnx
from onnx import version_converter
import os
import sys

def convert_onnx_opset(input_path, output_path, target_opset=21):
    print(f"加载模型: {input_path}")
    model = onnx.load(input_path)
    current_opset = model.opset_import[0].version
    print(f"当前 opset: {current_opset}")

    if current_opset <= target_opset:
        print("模型版本已符合要求，直接复制")
        import shutil
        shutil.copy2(input_path, output_path)
        return True

    print(f"转换到 opset {target_opset}...")
    converted = version_converter.convert_version(model, target_opset)
    onnx.save(converted, output_path)
    print(f"转换完成，已保存至: {output_path}")
    return True

def main():
    default_input = "C:/Users/12113/Desktop/ultralytics-8.4.21/yolov8n.onnx"
    default_output = "src/main/resources/model/yolov8n_opset21.onnx"

    input_path = input(f"输入模型路径 (默认: {default_input}): ").strip()
    if not input_path:
        input_path = default_input

    output_path = input(f"输出模型路径 (默认: {default_output}): ").strip()
    if not output_path:
        output_path = default_output

    target = input("目标 opset 版本 (默认: 21): ").strip()
    target = int(target) if target else 21

    convert_onnx_opset(input_path, output_path, target)

if __name__ == "__main__":
    main()