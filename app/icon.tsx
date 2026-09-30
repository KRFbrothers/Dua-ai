import { ImageResponse } from "next/og";

export const size = { width: 32, height: 32 };
export const contentType = "image/png";

export default function Icon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: 32,
          height: 32,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#1c1915",
          color: "#f3e6d0",
          fontSize: 20,
          fontFamily: "Georgia",
        }}
      >
        D
      </div>
    ),
    size,
  );
}
