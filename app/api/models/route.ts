import { listModels } from "@/lib/models";

export function GET() {
  return Response.json({ models: listModels() });
}
