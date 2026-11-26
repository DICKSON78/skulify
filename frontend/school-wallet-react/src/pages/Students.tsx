import ResourcePage from '../components/ResourcePage';
import { RESOURCE_CONFIGS } from '../config/resources';

export default function Students() {
    return <ResourcePage config={RESOURCE_CONFIGS.students} />;
}
