'use client';

import {ClientHeader} from "@/components/user/client-header";
import {CreateModelForm} from "@/components/meta/create-model-form";

export default function CreateModelPage() {
    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='添加模型'/>
            <CreateModelForm />
        </div>
    );
}
